package com.clinicqueue.common.security;

import com.clinicqueue.common.error.ErrorCode;
import com.clinicqueue.common.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiting for login and public endpoints.
 * Keeps abusive guessing of public secrets throttled without leaking any
 * information about why a particular secret was rejected.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int loginPerMinute;
    private final int publicPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(int loginPerMinute, int publicPerMinute) {
        this.loginPerMinute = loginPerMinute;
        this.publicPerMinute = publicPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String key = null;
        int capacity = 0;
        if (path.startsWith("/api/v1/auth/login")) {
            key = "login:" + clientKey(request);
            capacity = loginPerMinute;
        } else if (path.startsWith("/api/v1/public/")) {
            key = "public:" + clientKey(request);
            capacity = publicPerMinute;
        }
        if (key != null) {
            final int bucketCapacity = capacity;
            Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(bucketCapacity));
            if (!bucket.tryConsume()) {
                log.warn("Rate limit exceeded path={} key={}", path, key);
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                ProblemDetail pd = ProblemDetail.forStatus(429);
                pd.setType(URI.create("https://clinicqueue.app/problems/rate-limited"));
                pd.setTitle("RATE_LIMITED");
                pd.setDetail("Too many requests. Please slow down and retry shortly.");
                pd.setProperty("code", ErrorCode.RATE_LIMITED.name());
                pd.setProperty("requestId", MDC.get(GlobalExceptionHandler.REQUEST_ID_MDC_KEY));
                pd.setProperty("timestamp", Instant.now().toString());
                response.getWriter().write(toJson(pd));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String toJson(ProblemDetail pd) {
        return "{\"type\":\"" + pd.getType() + "\",\"title\":\"" + pd.getTitle()
                + "\",\"status\":429,\"detail\":\"" + pd.getDetail()
                + "\",\"code\":\"RATE_LIMITED\",\"requestId\":\""
                + MDC.get(GlobalExceptionHandler.REQUEST_ID_MDC_KEY) + "\"}";
    }

    /** Minimal refill token bucket (1 token per second, burst = capacity). */
    private static final class Bucket {
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * (capacity / 60.0));
            lastRefillNanos = now;
        }
    }
}
