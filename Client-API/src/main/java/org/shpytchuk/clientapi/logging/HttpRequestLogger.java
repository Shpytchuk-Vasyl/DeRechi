package org.shpytchuk.clientapi.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class HttpRequestLogger extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLogger.class);

    private static final List<String> SKIPPED = List.of("/graphql", "/graphiql", "/actuator");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !log.isInfoEnabled() || SKIPPED.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
            log.info("HTTP {} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        } catch (IOException | ServletException | RuntimeException failure) {
            log.error("HTTP {} {} -> failed in {} ms", request.getMethod(), request.getRequestURI(),
                    (System.nanoTime() - start) / 1_000_000, failure);
            throw failure;
        }
    }
}
