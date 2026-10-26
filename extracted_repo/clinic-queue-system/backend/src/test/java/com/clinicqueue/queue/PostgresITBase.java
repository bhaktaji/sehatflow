package com.clinicqueue.queue;

import com.clinicqueue.audit.AuditService;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.ClinicRepository;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.clinic.DoctorRepository;
import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.identity.Role;
import com.clinicqueue.identity.StaffUser;
import com.clinicqueue.identity.StaffUserRepository;
import com.clinicqueue.publicview.PublicViewService;
import com.clinicqueue.queue.dto.QueueDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Shared base for PostgreSQL-backed integration tests. Every test builds its
 * own clinic/staff/session rows so tests are independent and idempotent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class PostgresITBase {

    protected static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected ClinicRepository clinicRepository;
    @Autowired
    protected DoctorRepository doctorRepository;
    @Autowired
    protected StaffUserRepository staffUserRepository;
    @Autowired
    protected QueueSessionRepository sessionRepository;
    @Autowired
    protected QueueTokenRepository tokenRepository;
    @Autowired
    protected AuditService auditService;
    @Autowired
    protected QueueSessionService sessionService;
    @Autowired
    protected QueueService queueService;
    @Autowired
    protected PublicViewService publicViewService;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Clinic createClinic() {
        String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
        return clinicRepository.save(new Clinic("Test Clinic " + slug, slug, "Asia/Kolkata"));
    }

    protected Doctor createDoctor(Clinic clinic, String name) {
        return doctorRepository.save(new Doctor(clinic.getId(), name, "General"));
    }

    protected StaffUser createStaff(Clinic clinic, Role role, String username, Doctor doctor) {
        return staffUserRepository.save(new StaffUser(clinic.getId(), username, username,
                passwordEncoder.encode(username + "-pass"), role, doctor == null ? null : doctor.getId()));
    }

    protected ClinicUserPrincipal principalOf(StaffUser user) {
        return new ClinicUserPrincipal(user.getId(), user.getClinicId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getDoctorId(), user.getPasswordHash());
    }

    protected QueueSession createSession(Clinic clinic, Doctor doctor, LocalDate businessDate) {
        return sessionRepository.save(new QueueSession(clinic.getId(), doctor.getId(), businessDate,
                "A", 10, "hash", java.time.Instant.now()));
    }

    protected QueueDtos.SessionResponse openSessionViaService(Clinic clinic, Doctor doctor, LocalDate date) {
        StaffUser owner = createStaff(clinic, Role.OWNER, "owner-" + UUID.randomUUID().toString().substring(0, 6), null);
        return sessionService.createSession(principalOf(owner), new QueueDtos.CreateSessionRequest(doctor.getId(), date, null, null));
    }

    protected com.clinicqueue.queue.dto.QueueDtos.TokenResponse checkIn(Clinic clinic, QueueSession session,
                                                                        String displayName) {
        StaffUser reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        return queueService.checkIn(principalOf(reception), session.getId(),
                new QueueDtos.CreateTokenRequest(displayName, null));
    }
}
