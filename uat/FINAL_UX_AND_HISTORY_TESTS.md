# SehatFlow — Final UX, Logout, Consultation History & Data Retention Test Plan

## 1. Status-Bar & Safe-Area Inset Verification
| Test ID | Scenario | Target Screen / Component | Expected Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `UX-INSET-01` | Launcher / Login screen top-right settings icon (`server_settings_button`) under edge-to-edge rendering | `LoginScreen.kt` | `Row` consumes `WindowInsets.safeDrawing.only(Top + Horizontal)`; icon sits cleanly below status bar with 48dp touch target (`minimumInteractiveComponentSize()`). | **PASS** |
| `UX-INSET-02` | Light & Dark theme status-bar / navigation-bar icon contrast | `Theme.kt` | `WindowCompat.getInsetsController` updates `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` dynamically. | **PASS** |
| `UX-INSET-03` | All secondary screens (Patient, Doctor, Receptionist, Clinic Admin, Onboarding, Subscription) | Material 3 `TopAppBar` | Every screen uses M3 `TopAppBar` inside `Scaffold`, preventing status bar overlap across portrait, landscape, and cutout displays. | **PASS** |

---

## 2. Patient Logout & Cross-Session Isolation Tests
| Test ID | Scenario | Steps | Expected Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `AUTH-LOGOUT-01` | Discoverable logout button on Patient Dashboard & Live Queue Screen | Open `ClinicSelectScreen` or `PatientLiveQueueScreen`; inspect top-right app bar actions. | `patient_logout_btn` is visible with a 48dp touch target and accessible content description. | **PASS** |
| `AUTH-LOGOUT-02` | Logout confirmation dialog & cancel safety | Tap `patient_logout_btn`, then tap `Cancel` (`patient_logout_cancel_btn`). | Confirmation dialog (`patient_logout_confirm_dialog`) closes; patient session and active queue token remain intact. | **PASS** |
| `AUTH-LOGOUT-03` | Full session termination, local Room cache wipe, and backstack reset | Tap `patient_logout_btn`, then tap `Log Out` (`patient_logout_confirm_btn`). | `PatientViewModel.clearPatientSessionState()` clears in-memory state; `QueueTokenDao.clearAllCachedTokens()` wipes local cached tokens; `SessionPreferences` cleared; navigates to `Login` with `popUpTo(0) { inclusive = true }`. | **PASS** |
| `AUTH-LOGOUT-04` | Multi-patient shared-device isolation (Patient A -> Logout -> Patient B) | Log in as `pat_1`, view history, log out, then log in as `pat_2`. | `pat_2` sees only `pat_2` records and never sees `pat_1` cached tokens or consultation records. | **PASS** |

---

## 3. Consultation History, Multi-Clinic Snapshots & Role Filter Tests
| Test ID | Scenario | Role | Expected Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `HIST-DOM-01` | Consultation History vs. Appointment/Token History separation | All Roles | Completed encounters (`RecordCategory.CONSULTATION`) are distinct from cancelled/no-show queue tokens (`RecordCategory.APPOINTMENT_TOKEN`). | **PASS** |
| `HIST-SNAP-01` | Multi-clinic & multi-chamber snapshot preservation (`Dr. John` at `XYZ Clinic` Chamber A vs `ABC Clinic` Chamber B) | Doctor (`doc_john`) | Filtering by `XYZ Clinic` returns `Chamber A (Mon/Wed/Fri)` records; filtering by `ABC Clinic` returns `Chamber B (Tue/Thu)` records with immutable historical snapshots. | **PASS** |
| `HIST-RET-01` | Default 3-month active window (`LAST_3_MONTHS`) vs. All-Time archived record retrieval (`ALL_TIME_WITH_ARCHIVED`) | Doctor / Patient / Admin | Default view returns records within last 90 days; switching to `All Time (Incl. Archived)` retrieves 140-day and 210-day archived records (`cons_hist_archived_201`, `cons_hist_archived_202`) without data loss. | **PASS** |
| `HIST-RBAC-01` | Receptionist & Clinic Admin tenant isolation and clinical note redaction | Receptionist / Owner (`clinic_1`) | Can only view `XYZ Clinic` records; attempting to filter `ABC Clinic` returns 0 records; private `doctorNotes` and `diagnosisSummary` are redacted. | **PASS** |
| `HIST-ADD-01` | Versioned clinical addendum & audit logging | Doctor (`doc_john`) | Authoring doctor appends addendum (`v1 -> v2`) while preserving original `clinicalSummary`; non-author doctor (`doc_donna`) is rejected; `ClinicalAuditLogger` records `ADDENDUM_APPENDED`. | **PASS** |
| `HIST-EDGE-01` | Invalid custom date range (`startMillis > endMillis`) | All Roles | `hasInvalidCustomDateRange()` detects invalid range and returns empty list safely without crashing. | **PASS** |
