package com.clinicqueue.identity;

import com.clinicqueue.identity.dto.IdentityDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Bootstraps the CSRF token: a fresh token is generated and written to the
     * XSRF-TOKEN cookie (HttpOnly=false so the SPA can echo it back in the
     * X-XSRF-TOKEN header for state-changing requests).
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        return ResponseEntity.ok(Map.of("headerName", "X-XSRF-TOKEN", "cookieName", "XSRF-TOKEN"));
    }

    @GetMapping("/me")
    public ResponseEntity<IdentityDtos.UserInfoResponse> me(@AuthenticationPrincipal ClinicUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new IdentityDtos.UserInfoResponse(
                principal.getUserId(), principal.getClinicId(), null, principal.getUsername(),
                principal.getDisplayName(), principal.getRole().name(), principal.getDoctorId()));
    }
}
