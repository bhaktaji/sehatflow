package com.clinicqueue.clinic;

import com.clinicqueue.clinic.dto.ClinicDtos;
import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.common.error.ErrorCode;
import com.clinicqueue.identity.ClinicUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DoctorController {

    private final DoctorRepository doctorRepository;

    public DoctorController(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @GetMapping("/doctors")
    public List<ClinicDtos.DoctorResponse> list(@AuthenticationPrincipal ClinicUserPrincipal principal) {
        return doctorRepository.findByClinicId(principal.getClinicId()).stream()
                .map(d -> new ClinicDtos.DoctorResponse(d.getId(), d.getDisplayName(), d.getSpecialty(), d.isActive()))
                .toList();
    }

    @PostMapping("/doctors")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ClinicDtos.DoctorResponse> create(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                            @Valid @RequestBody ClinicDtos.CreateDoctorRequest request) {
        Doctor doctor = new Doctor(principal.getClinicId(), request.displayName().trim(), request.specialty());
        doctor = doctorRepository.save(doctor);
        return ResponseEntity.created(URI.create("/api/v1/doctors/" + doctor.getId()))
                .body(new ClinicDtos.DoctorResponse(doctor.getId(), doctor.getDisplayName(),
                        doctor.getSpecialty(), doctor.isActive()));
    }

    @PatchMapping("/doctors/{doctorId}")
    @PreAuthorize("hasRole('OWNER')")
    public ClinicDtos.DoctorResponse update(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                            @PathVariable UUID doctorId,
                                            @Valid @RequestBody ClinicDtos.UpdateDoctorRequest request) {
        Doctor doctor = doctorRepository.findByIdAndClinicId(doctorId, principal.getClinicId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Doctor not found"));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            doctor.setDisplayName(request.displayName().trim());
        }
        if (request.specialty() != null) {
            doctor.setSpecialty(request.specialty().isBlank() ? null : request.specialty().trim());
        }
        if (request.active() != null) {
            doctor.setActive(request.active());
        }
        doctor = doctorRepository.save(doctor);
        return new ClinicDtos.DoctorResponse(doctor.getId(), doctor.getDisplayName(),
                doctor.getSpecialty(), doctor.isActive());
    }
}
