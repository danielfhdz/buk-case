package com.bukcase.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Demo stand-in for the existing authentication layer: binds the user given in the
 * {@code X-User-Id} header to the request thread.
 */
@Component
public class CurrentUserFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(USER_HEADER);
        if (header == null || header.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        try (CurrentUser.Scope ignored = CurrentUser.bind(Long.parseLong(header.trim()))) {
            chain.doFilter(request, response);
        }
    }
}
