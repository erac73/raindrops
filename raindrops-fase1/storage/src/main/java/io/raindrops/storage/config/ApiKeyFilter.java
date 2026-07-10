package io.raindrops.storage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public ApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader("X-API-Key");

        if (providedKey != null && providedKey.equals(expectedApiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Missing or invalid API key\"}");
    }
}
