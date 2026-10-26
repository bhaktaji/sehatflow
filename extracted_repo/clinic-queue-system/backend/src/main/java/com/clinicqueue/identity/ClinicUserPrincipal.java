package com.clinicqueue.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated staff principal. clinicId always comes from the server-side
 * principal, never from a request body or parameter.
 */
public class ClinicUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID clinicId;
    private final String username;
    private final String displayName;
    private final Role role;
    private final UUID doctorId;
    private final String passwordHash;

    public ClinicUserPrincipal(UUID userId, UUID clinicId, String username, String displayName,
                               Role role, UUID doctorId, String passwordHash) {
        this.userId = userId;
        this.clinicId = clinicId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.doctorId = doctorId;
        this.passwordHash = passwordHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
