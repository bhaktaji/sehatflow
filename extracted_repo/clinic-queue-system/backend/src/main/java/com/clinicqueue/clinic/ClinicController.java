package com.clinicqueue.clinic;

import com.clinicqueue.clinic.dto.ClinicDtos;
import com.clinicqueue.identity.ClinicUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClinicController {

    private final ClinicRepository clinicRepository;

    public ClinicController(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    @GetMapping("/clinic")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST','DOCTOR')")
    public ClinicDtos.ClinicResponse current(@AuthenticationPrincipal ClinicUserPrincipal principal) {
        Clinic clinic = clinicRepository.findById(principal.getClinicId())
                .orElseThrow(() -> new com.clinicqueue.common.error.ApiException(
                        com.clinicqueue.common.error.ErrorCode.NOT_FOUND, "Clinic not found"));
        return new ClinicDtos.ClinicResponse(clinic.getId(), clinic.getName(), clinic.getSlug(),
                clinic.getTimezone(), clinic.isActive());
    }
}
