package com.redrivermutual.claims.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rejects API requests whose declared body is larger than the limit (64 KB by default) with 413,
 * before any of it is parsed. A claim is a few kilobytes, so anything bigger is a mistake or abuse.
 *
 * <p>Limitation: a chunked request has no Content-Length and is not checked here. In production
 * the edge proxy (Nginx {@code client_max_body_size}) enforces the same limit for every request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxRequestBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(@Value("${claims.api.max-request-bytes:65536}") long maxRequestBytes,
                                  ObjectMapper objectMapper) {
        this.maxRequestBytes = maxRequestBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() <= maxRequestBytes) {
            chain.doFilter(request, response);
            return;
        }

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", ProblemTypes.PAYLOAD_TOO_LARGE.toString());
        problem.put("title", "Request is too large");
        problem.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        problem.put("detail", "The request body must be " + (maxRequestBytes / 1024) + " KB or smaller.");
        problem.put("instance", request.getRequestURI());
        problem.put("correlationId", CorrelationIdFilter.currentCorrelationId());

        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
