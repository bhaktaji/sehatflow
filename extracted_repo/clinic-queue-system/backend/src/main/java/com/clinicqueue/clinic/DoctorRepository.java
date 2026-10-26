package com.clinicqueue.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    List<Doctor> findByClinicId(UUID clinicId);

    Optional<Doctor> findByIdAndClinicId(UUID id, UUID clinicId);
}
