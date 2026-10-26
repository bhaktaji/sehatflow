package com.clinicqueue.identity;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.ClinicRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

/**
 * Resolves a username to a StaffUser. Usernames are unique per clinic, so a
 * username used by several clinics is disambiguated with the clinic slug from
 * the login request (carried on authentication details). All failures surface
 * as generic bad-credentials so the response never discloses which accounts
 * exist.
 */
public class ClinicAuthenticationProvider implements AuthenticationProvider {

    private final StaffUserRepository staffUserRepository;
    private final ClinicRepository clinicRepository;
    private final PasswordEncoder passwordEncoder;

    public ClinicAuthenticationProvider(StaffUserRepository staffUserRepository,
                                        ClinicRepository clinicRepository,
                                        PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.clinicRepository = clinicRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken token)) {
            return null;
        }
        String username = String.valueOf(token.getName());
        String password = token.getCredentials() == null ? "" : String.valueOf(token.getCredentials());

        List<StaffUser> matches = staffUserRepository.findByUsername(username);
        if (matches.isEmpty()) {
            throw new BadCredentialsException("invalid credentials");
        }
        StaffUser user = matches.get(0);
        if (matches.size() > 1) {
            String clinicSlug = token.getDetails() == null ? null : token.getDetails().toString();
            if (clinicSlug == null || clinicSlug.isBlank()) {
                throw new BadCredentialsException("invalid credentials: clinic code required");
            }
            UUID clinicId = clinicRepository.findBySlug(clinicSlug)
                    .map(Clinic::getId)
                    .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
            user = matches.stream()
                    .filter(candidate -> candidate.getClinicId().equals(clinicId))
                    .findFirst()
                    .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        }
        if (!user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }
        UserDetails principal = new ClinicUserPrincipal(user.getId(), user.getClinicId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getDoctorId(), user.getPasswordHash());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
