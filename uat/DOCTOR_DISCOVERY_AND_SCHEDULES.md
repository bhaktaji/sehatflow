# SehatFlow UAT Report: Doctor Discovery & Multi-Clinic Weekly Schedules

## 1. Executive Summary & Objective
This report documents the verification and testing of **Doctor Discovery and Weekly Clinic Schedules** in SehatFlow, covering doctor search (exact and partial name matching), specialization filtering, professional credential display, and multi-clinic schedule management.

---

## 2. Test Architecture & Synthetic Test Data

### A. Target Scenario Profile: Dr. John
| Attribute | Configured Value | Verification Status |
|---|---|---|
| **Full Name** | Dr. John | Verified |
| **Specialization** | General Physician | Verified |
| **Qualifications** | MBBS, MD (General Medicine) | Verified (Synthetic credential) |
| **Medical Registration** | Reg. #SYNTH-48201 | Verified (Synthetic credential) |
| **Clinical Experience** | 10 Years Clinical Practice | Verified |
| **Primary Clinic** | XYZ Clinic | Verified |
| **Primary Chamber** | Chamber A | Verified |

### B. Weekly Multi-Clinic Schedule Matrix
| Day of Week | Clinic | Chamber | Hours | Daily Capacity Limit | Status |
|---|---|---|---|---:|---|
| **Monday** | XYZ Clinic | Chamber A | 09:00 AM - 01:00 PM | 10 | Active (10/10 Full in Test D) |
| **Tuesday** | XYZ Clinic | Chamber A | 09:00 AM - 01:00 PM | 10 | Active (4/10 Booked, 6 Available) |
| **Wednesday** | ABC Clinic | Chamber B | 02:00 PM - 06:00 PM | 10 | Active (2/10 Booked, 8 Available) |
| **Thursday** | ABC Clinic | Chamber B | 02:00 PM - 06:00 PM | 10 | Active (1/10 Booked, 9 Available) |
| **Friday** | No Scheduled Clinic | — | Closed | 0 | Off Duty (`isDoctorAvailable = false`) |
| **Saturday** | No Scheduled Clinic | — | Closed | 0 | Off Duty (`isDoctorAvailable = false`) |
| **Sunday** | No Scheduled Clinic | — | Closed | 0 | Off Duty (`isDoctorAvailable = false`) |

---

## 3. Test Cases & Verification Results

### Test A: Doctor Search
- **TC-DOC-001 (Complete Name Search)**:
  - Input: `"Dr. John"`
  - Result: Returned Dr. John card (`doctor_card_doc_john`) with 100% accuracy.
- **TC-DOC-002 (Partial Name Search)**:
  - Input: `"John"`, `"Dr."`, `"Jo"`
  - Result: Correctly matched Dr. John without returning unrelated specialists.
- **TC-DOC-003 (Specialization Filter Chips)**:
  - Tapping **"General Physician"** chip filters list to Dr. John.
  - Tapping **"Pediatrics"** chip filters list to Dr. Donna.
  - Tapping **"Orthopedics"** chip filters list to Dr. Alex.
  - Tapping **"All"** restores the full clinical roster.
- **TC-DOC-004 (Empty Search Handling)**:
  - Input: `"NonExistentSurgeon"`
  - Result: Displays clean empty-state affordance: *"No doctors found matching 'NonExistentSurgeon'. Try a different name or specialty."*

### Test B: Doctor Profile & Multi-Clinic Schedule Dialog
- **TC-DOC-005 (Profile Inspection)**:
  - Tapping **"Schedule & Profile"** (`view_profile_btn_doc_john`) launches the `DoctorProfileDialog`.
  - Displays professional credentials, qualifications, verified synthetic registration number, and clinical experience.
- **TC-DOC-006 (Schedule Distinguishability)**:
  - Patients can clearly distinguish between XYZ Clinic (Monday & Tuesday, Chamber A) and ABC Clinic (Wednesday & Thursday, Chamber B).
  - Friday, Saturday, and Sunday are labeled with "No Clinic Scheduled" badges.
  - Monday displays real-time capacity badge: **"FULL (10/10)"** with fallback notice.
  - Tuesday displays real-time capacity badge: **"4/10 Booked (6 Available)"**.
- **TC-DOC-007 (Direct Slot Booking)**:
  - Clicking an eligible schedule slot initiates the booking workflow for that specific clinic and doctor without routing errors.

---

## 4. Automated Regression Verification
- **Automated Test**: `SehatFlowUatRegressionTest.testDoctorDiscovery_completeAndPartialNameSearch` — **PASSED**
- **Automated Test**: `SehatFlowUatRegressionTest.testDoctorSchedule_multiClinicWeeklyMatrix` — **PASSED**
