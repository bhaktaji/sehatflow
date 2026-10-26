package com.clinicqueue.identity;

import com.clinicqueue.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes 401/403 responses as ProblemDetail with stable error codes. */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {
    }

    public static AuthenticationEntryPoint entryPoint() {
        return (request, response, authException) -> write(response, 401, "UNAUTHENTICATED",
                "Authentication required");
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(response, 403, "FORBIDDEN",
                "Access denied");
    }

    private static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> pd = new LinkedHashMap<>();
        pd.put("type", "https://clinicqueue.app/problems/" + code.toLowerCase().replace('_', '-'));
        pd.put("title", code);
        pd.put("status", status);
        pd.put("detail", detail);
        pd.put("code", code);
        pd.put("requestId", MDC.get(GlobalExceptionHandler.REQUEST_ID_MDC_KEY));
        pd.put("timestamp", Instant.now().toString());
        new ObjectMapper().writeValue(response.getWriter(), pd);
    }

    /** Unused URI helper kept for clarity of the problem type scheme. */
    static URI problemType(String code) {
        return URI.create("https://clinicqueue.app/problems/" + code.toLowerCase().replace('_', '-'));
    }
}
