package com.clinicqueue.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    List<StaffUser> findByUsername(String username);

    Optional<StaffUser> findByUsernameAndClinicId(String username, UUID clinicId);

    boolean existsByClinicIdAndUsername(UUID clinicId, String username);
}
