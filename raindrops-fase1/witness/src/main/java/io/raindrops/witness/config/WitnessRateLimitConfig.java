package io.raindrops.witness.config;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WitnessRateLimitConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WitnessRateLimitConfig.class);

    private final ConcurrentHashMap<String, RateBucket> buckets = new ConcurrentHashMap<>();

    private static final long BUCKET_MAX_AGE_MS = 5 * 60 * 1000;

    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "witness-rate-limit-cleanup");
                t.setDaemon(true);
                return t;
            });

    {
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldBuckets, 60, 60, TimeUnit.SECONDS);
    }

    void cleanupOldBuckets() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, RateBucket>> it = buckets.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<String, RateBucket> entry = it.next();
            if (now - entry.getValue().getLastAccess() > BUCKET_MAX_AGE_MS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired rate-limit buckets", removed);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/witness/**");
    }

    public class RateLimitInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) throws Exception {
            String clientIp = getClientIp(request);
            String method = request.getMethod();
            String key = clientIp + ":" + method;

            RateBucket bucket = buckets.computeIfAbsent(key, k -> new RateBucket(getLimit(method)));

            if (!bucket.tryConsume()) {
                log.warn("Rate limit exceeded for {} {} from {}", method, request.getRequestURI(), clientIp);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":"
                    + bucket.getResetTime() + "}");
                return false;
            }

            return true;
        }

        private String getClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }

        private int getLimit(String method) {
            return switch (method) {
                case "POST" -> 50;    // STORE/RECONSTRUCT operations (more expensive)
                case "GET" -> 100;    // READ operations
                default -> 50;
            };
        }
    }

    static class RateBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefill;
        private final AtomicLong lastAccess;
        private final long refillIntervalMs = 60_000; // 1 minute

        RateBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefill = new AtomicLong(System.currentTimeMillis());
            this.lastAccess = new AtomicLong(System.currentTimeMillis());
        }

        boolean tryConsume() {
            lastAccess.set(System.currentTimeMillis());
            refillIfNeeded();
            int current = tokens.get();
            while (current > 0) {
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = tokens.get();
            }
            return false;
        }

        long getLastAccess() {
            return lastAccess.get();
        }

        int getResetTime() {
            return (int) ((refillIntervalMs - (System.currentTimeMillis() - lastRefill.get())) / 1000);
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            long last = lastRefill.get();
            if (now - last >= refillIntervalMs) {
                if (lastRefill.compareAndSet(last, now)) {
                    tokens.set(maxTokens);
                }
            }
        }
    }
}
