package com.clinicqueue.common.config;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.ClinicRepository;
import com.clinicqueue.identity.Role;
import com.clinicqueue.identity.StaffUser;
import com.clinicqueue.identity.StaffUserRepository;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.clinic.DoctorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent demo seeding. Runs after Flyway has built the schema and only
 * when the database is empty, so a clean checkout is runnable immediately and
 * regression tests get deterministic data.
 */
@Component
public class SeedRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final ClinicRepository clinicRepository;
    private final DoctorRepository doctorRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;

    public SeedRunner(ClinicRepository clinicRepository, DoctorRepository doctorRepository,
                      StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder,
                      @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.clinicRepository = clinicRepository;
        this.doctorRepository = doctorRepository;
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!seedEnabled || clinicRepository.count() > 0) {
            return;
        }
        Clinic clinic = clinicRepository.save(new Clinic(
                "Sunrise Family Clinic", "sunrise-family", "Asia/Kolkata"));
        Doctor doctor = doctorRepository.save(new Doctor(
                clinic.getId(), "Dr. Asha Menon", "Family Physician"));

        createStaff(clinic.getId(), doctor.getId(), "Ravi Sharma", "owner", "owner123", Role.OWNER);
        createStaff(clinic.getId(), null, "Priya Desk", "reception", "recep123", Role.RECEPTIONIST);
        createStaff(clinic.getId(), doctor.getId(), "Dr. Asha Menon", "doctor", "doc123", Role.DOCTOR);

        log.info("Seeded demo clinic '{}' (slug={}) with owner/reception/doctor accounts",
                clinic.getName(), clinic.getSlug());
    }

    private void createStaff(UUID clinicId, UUID doctorId, String displayName, String username,
                             String password, Role role) {
        staffUserRepository.save(new StaffUser(clinicId, displayName, username,
                passwordEncoder.encode(password), role, doctorId));
    }
}
