package io.raindrops.witness.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WitnessAuditLogInterceptor implements HandlerInterceptor {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute("requestId", requestId);
        request.setAttribute("startTime", System.currentTimeMillis());

        String clientIp = getClientIp(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();

        auditLog.info("[AUDIT] REQUEST id={} method={} uri={} client={}",
            requestId, method, uri, clientIp);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String requestId = (String) request.getAttribute("requestId");
        Long startTime = (Long) request.getAttribute("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        int status = response.getStatus();
        String level = status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO";

        if (level.equals("ERROR")) {
            auditLog.error("[AUDIT] RESPONSE id={} status={} duration={}ms",
                requestId, status, duration);
        } else if (level.equals("WARN")) {
            auditLog.warn("[AUDIT] RESPONSE id={} status={} duration={}ms",
                requestId, status, duration);
        } else {
            auditLog.info("[AUDIT] RESPONSE id={} status={} duration={}ms",
                requestId, status, duration);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
