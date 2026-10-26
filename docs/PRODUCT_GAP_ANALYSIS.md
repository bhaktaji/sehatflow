# SehatFlow — Product Gap Analysis & Architectural Review

## 1. Executive Summary
This document captures the comprehensive audit of SehatFlow across user experience, session lifecycle management, clinical record domain modeling, multi-clinic/multi-chamber workflows, and long-term healthcare data governance.

---

## 2. Identified Product & UX Gaps

### Gap 1: Launcher / Login Screen Status-Bar Overlap (`screenshot_2_023c04d7.png`)
- **Observation:** On the authentication screen (`LoginScreen.kt`), the top-right server settings gear icon (`server_settings_button`) overlapped the Android system status bar (clock, Wi-Fi, battery indicators) under edge-to-edge rendering (`enableEdgeToEdge()`).
- **Root Cause:** Unlike secondary screens that used Material 3 `TopAppBar` (which consumes `WindowInsets.systemBars` automatically), `LoginScreen.kt` used a raw `Row` inside `Scaffold(topBar = { Row(...) })` without applying `WindowInsets.safeDrawing` top/horizontal padding. Additionally, `Theme.kt` lacked explicit `WindowCompat.getInsetsController` status/navigation bar icon appearance synchronization.
- **Resolution:**
  - Applied `.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))` and `.minimumInteractiveComponentSize()` (48dp touch target) to `LoginScreen.kt`'s top bar.
  - Added `SideEffect` in `Theme.kt` to synchronize `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` across light and dark themes.
  - Verified all other screens (`ClinicSelectScreen`, `PatientLiveQueueScreen`, `VisitHistoryScreen`, `UploadPrescriptionScreen`, `ReceptionDashboardScreen`, `DoctorDashboardScreen`, `DoctorConsultationHistoryScreen`, `ClinicAdminDashboardScreen`, `ClinicOperationalHistoryScreen`, `OnboardingScreen`, `ClinicSubscriptionScreen`) use Material 3 `TopAppBar` with safe-area inset compliance.

### Gap 2: Missing Patient Logout Option (`screenshot_3_b6146a64.png`)
- **Observation:** Patients logged into the Patient Dashboard (`ClinicSelectScreen`) or Live Queue Tracker (`PatientLiveQueueScreen`) had no visible UI action to sign out, switch accounts, or terminate their session on shared family devices.
- **Root Cause:** `ClinicSelectScreen` only exposed a Visit History icon in `TopAppBar.actions`, and `PatientLiveQueueScreen` had no `actions` block in its `TopAppBar`. Furthermore, `AuthRepository.logout()` cleared `SessionPreferences` and Firebase Auth but did not purge locally cached patient queue tokens in Room (`QueueTokenDao`) or reset `PatientViewModel` in-memory state.
- **Resolution:**
  - Added a discoverable Logout button (`patient_logout_btn`) in `ClinicSelectScreen` and `PatientLiveQueueScreen` with a confirmation dialog (`patient_logout_confirm_dialog`).
  - Enhanced `AuthRepository.logout()` to invoke `queueTokenDao?.clearAllCachedTokens()`, sign out of Firebase Auth cleanly without revoking global device Google account permissions, and clear `SessionPreferences`.
  - Added `PatientViewModel.clearPatientSessionState()` and an automatic `currentSession == null` observer to wipe in-memory patient tokens and history immediately upon logout.
  - Configured `AppNavHost` to reset the navigation backstack (`popUpTo(0) { inclusive = true }`) to `NavRoute.Login.route`.

### Gap 3: Conflation of Queue Token History with Clinical Consultation History
- **Observation:** Previously, patient history only listed basic `QueueToken` items, conflating cancelled/no-show queue tokens with completed medical consultations, and providing no historical consultation view for Doctors, Receptionists, or Clinic Administrators.
- **Resolution:**
  - Separated `RecordCategory.CONSULTATION` (completed clinical consultations with immutable clinic/chamber/doctor snapshots, clinical summaries, prescription links, and versioned addenda) from `RecordCategory.APPOINTMENT_TOKEN` (operational queue/booking lifecycle events including `NO_SHOW` and `CANCELLED`).
  - Built dedicated, role-isolated historical views:
    - **Doctor:** `DoctorConsultationHistoryScreen` (multi-clinic, multi-chamber, clinical notes, versioned addendum workflow).
    - **Receptionist & Clinic Admin:** `ClinicOperationalHistoryScreen` (clinic-scoped operational history and summary metrics with clinical note redaction).
    - **Patient:** Upgraded `VisitHistoryScreen` (consultation vs. appointment filter, prescription filter, date range selector, and clinical summary modal).

### Gap 4: Multi-Clinic & Multi-Chamber Historical Snapshot Integrity
- **Observation:** In outpatient practice, a single doctor frequently consults across multiple clinics and chambers on different days (e.g., `Dr. John` at `XYZ Clinic` Chamber A on Mon/Wed/Fri and `ABC Clinic` Chamber B on Tue/Thu). Storing only foreign keys causes historical records to mutate if a doctor later changes schedules or chambers.
- **Resolution:**
  - Every `ConsultationRecord` captures immutable point-in-time snapshots: `clinicId`, `clinicNameSnapshot`, `chamberId`, `chamberNameSnapshot`, `doctorId`, `doctorNameSnapshot`, `doctorSpecializationSnapshot`, `tokenNumber`, `appointmentCreatedAt`, `consultationStartedAt`, and `consultationCompletedAt`.

---

## 3. Additional Healthcare Workflow Safeguards Implemented
1. **Least-Privilege Clinical Redaction:** Receptionists and Clinic Administrators inspecting `ClinicOperationalHistoryScreen` can view operational queue/consultation completion metrics and prescription attachment presence, while private `doctorNotes` and `diagnosisSummary` are automatically redacted by `HistoryFilterEngine`.
2. **Versioned Clinical Addenda (Non-Destructive Corrections):** Completed consultations are immutable. If an authoring doctor needs to record post-visit lab results or clinical clarifications, `addClinicalAddendum` increments `recordVersion`, preserves the original `clinicalSummary` and `doctorNotes`, appends a timestamped addendum, and records an entry in `ClinicalAuditLogger`.
3. **Configurable Data Retention & Archival Policy:** Defaults UI queries to the active 3-month window (`LAST_3_MONTHS`) for fast rendering while preserving all older records (`ARCHIVED_SEARCHABLE`) for on-demand retrieval without destructive auto-deletion.
