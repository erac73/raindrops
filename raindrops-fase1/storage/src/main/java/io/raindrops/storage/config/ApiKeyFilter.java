package io.raindrops.storage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public ApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader("X-API-Key");

        if (providedKey != null && MessageDigest.isEqual(providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), expectedApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Missing or invalid API key\"}");
    }
}
