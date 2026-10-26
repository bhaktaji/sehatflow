package com.clinicqueue.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

/**
 * Deterministic cookie CSRF repository for the SPA: the cookie value IS the
 * token, so validation is a simple header-vs-cookie comparison (the classic
 * CookieCsrfTokenRepository semantics). The /api/v1/auth/csrf endpoint
 * generates and saves the token explicitly, which writes the XSRF-TOKEN
 * cookie; the frontend echoes it in the X-XSRF-TOKEN header.
 */
public class CookieApiCsrfTokenRepository implements CsrfTokenRepository {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, UUID.randomUUID().toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = (token == null)
                ? ResponseCookie.from(COOKIE_NAME, "").maxAge(0).path("/").sameSite("Lax").build()
                : ResponseCookie.from(COOKIE_NAME, token.getToken()).path("/").sameSite("Lax").build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, cookie.getValue());
            }
        }
        return null;
    }
}
