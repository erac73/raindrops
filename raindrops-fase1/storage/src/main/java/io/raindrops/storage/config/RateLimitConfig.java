package io.raindrops.storage.config;

import java.util.concurrent.ConcurrentHashMap;
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
public class RateLimitConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    private final ConcurrentHashMap<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/drops/**", "/rainmaps/**");
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
                case "POST" -> 100;   // STORE operations
                case "GET" -> 200;    // READ operations
                case "PUT" -> 50;     // UPDATE operations
                case "DELETE" -> 20;  // DELETE operations
                default -> 100;
            };
        }
    }

    static class RateBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefill;
        private final long refillIntervalMs = 60_000; // 1 minute

        RateBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefill = new AtomicLong(System.currentTimeMillis());
        }

        boolean tryConsume() {
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
