package com.clinicqueue.identity;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.ClinicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON-body login filter for {@code POST /api/v1/auth/login}. The response
 * never discloses whether the username or the password was wrong.
 */
public class JsonLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper;
    private final ClinicRepository clinicRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public JsonLoginFilter(ObjectMapper objectMapper, ClinicRepository clinicRepository) {
        this.objectMapper = objectMapper;
        this.clinicRepository = clinicRepository;
        setFilterProcessesUrl("/api/v1/auth/login");
        setAuthenticationSuccessHandler(new LoginSuccessHandler());
        setAuthenticationFailureHandler(new LoginFailureHandler());
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        if (!"POST".equals(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }
        LoginRequest body;
        try {
            body = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Malformed login request", e);
        }
        if (body.username() == null || body.username().isBlank() || body.password() == null || body.password().isBlank()) {
            throw new AuthenticationServiceException("Username and password are required");
        }
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password());
        // clinicSlug rides on details so the provider can disambiguate a username
        // that exists in more than one clinic.
        token.setDetails(body.clinicSlug());
        Authentication authentication = getAuthenticationManager().authenticate(token);

        // Session fixation protection: drop any pre-login session and start fresh.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return authentication;
    }

    private class LoginSuccessHandler implements AuthenticationSuccessHandler {
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException {
            ClinicUserPrincipal user = (ClinicUserPrincipal) authentication.getPrincipal();
            String clinicName = clinicRepository.findById(user.getClinicId()).map(Clinic::getName).orElse("");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", user.getUserId().toString());
            payload.put("clinicId", user.getClinicId().toString());
            payload.put("clinicName", clinicName);
            payload.put("username", user.getUsername());
            payload.put("displayName", user.getDisplayName());
            payload.put("role", user.getRole().name());
            payload.put("doctorId", user.getDoctorId() == null ? null : user.getDoctorId().toString());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), payload);
        }
    }

    private static class LoginFailureHandler
            implements org.springframework.security.web.authentication.AuthenticationFailureHandler {
        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                            AuthenticationException exception) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            Map<String, Object> pd = new LinkedHashMap<>();
            pd.put("type", "https://clinicqueue.app/problems/invalid-credentials");
            pd.put("title", "INVALID_CREDENTIALS");
            pd.put("status", 401);
            pd.put("detail", "Invalid username or password");
            pd.put("code", "INVALID_CREDENTIALS");
            new ObjectMapper().writeValue(response.getWriter(), pd);
        }
    }

    /** JSON body of the login request. */
    public record LoginRequest(String username, String password, String clinicSlug) {
    }
}
