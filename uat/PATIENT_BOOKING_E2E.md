# SehatFlow Patient-to-Doctor Appointment Booking End-to-End Journey

## 1. Overview
This document verifies the complete, end-to-end appointment booking and digital queue token journey in SehatFlow, validating compliance with multi-clinic schedule isolation, hard daily capacity limits, fallback allocation rules, and DPDP Act 2023 consent.

---

## 2. Multi-Clinic Schedule & Capacity Matrix (Dr. John)

As defined in the UAT specification:

| Day of Week | Clinic Name | Chamber | Daily Capacity | Available / Policy |
| :--- | :--- | :--- | :--- | :--- |
| **Monday** | XYZ Clinic | Chamber A | 10 patients | 10/10 Booked (FULL) -> Triggers automatic Tuesday rollover |
| **Tuesday** | XYZ Clinic | Chamber A | 10 patients | 4/10 Booked (6 Available) |
| **Wednesday** | ABC Clinic | Chamber B | 10 patients | 10 Available (Distinct location separation) |
| **Thursday** | ABC Clinic | Chamber B | 10 patients | 10 Available |

### Key Business Invariants Verified:
1. **Clinic & Chamber Isolation**: Wednesday and Thursday are strictly at **ABC Clinic, Chamber B**. Under no circumstances does a Wednesday booking display XYZ Clinic or Chamber A.
2. **Hard 10-Patient Cap**: Daily capacity is strictly capped at 10 tokens per day.
3. **Same-Clinic Rollover**: When Monday at XYZ Clinic reaches 10/10 full capacity, the 11th token request rolls over to **Tuesday at XYZ Clinic**, never cross-leaking to ABC Clinic.
4. **Cancellation Slot Recycling**: Cancelling an existing token immediately decrements the approved count, freeing up the slot for a new patient.

---

## 3. End-to-End Patient Booking Journey Walkthrough

1. **Step 1: Patient Discovery (`ClinicSelectScreen`)**
   - Patient opens the app and browses available doctors.
   - Patient searches for "Dr. John" or selects "General Physician".
   - Doctor card displays:
     - Name: Dr. John
     - Specialty: General Physician • 12 yrs exp
     - Qualifications: MBBS, MD (Medicine) • Verified
     - Primary location: XYZ Clinic • Chamber A
     - Weekly schedule summary: 4 Clinic Days
2. **Step 2: Doctor Profile & Weekly Schedule (`DoctorProfileDialog`)**
   - Patient taps "Schedule & Profile".
   - Dialog displays full credentials and weekly schedule cards:
     - Monday: FULL (10/10) with triage notice.
     - Tuesday: 4/10 Booked (6 Available).
     - Wednesday: ABC Clinic, Chamber B (Available).
     - Thursday: ABC Clinic, Chamber B (Available).
   - Patient taps Wednesday slot.
3. **Step 3: Appointment Configuration (`UploadPrescriptionScreen`)**
   - Appointment Summary Card updates dynamically:
     - **Booking with: Dr. John**
     - **ABC Clinic • Chamber B • Wednesday (Max 10 patients/day)**
   - DPDP Act 2023 consent card is reviewed and accepted.
   - Patient uploads prescription using Android zero-permission Photo Picker or generic PDF picker.
   - Patient enters optional display name.
   - Patient taps "Submit Prescription & Request Token".
4. **Step 4: Receptionist Verification (`ReceptionDashboardScreen`)**
   - Prescription appears in Receptionist's `PrescriptionReviewSheet`.
   - Receptionist reviews image, verifies details, and clicks "Verify & Allocate Token".
5. **Step 5: Token Generation & Live Tracking (`PatientLiveQueueScreen`)**
   - Token is issued with status `WAITING`.
   - Live queue ETA calculator updates dynamically based on doctor's consultation pace.

---

## 4. Test Verification Evidence
Automated test suite execution in `SehatFlowUatRegressionTest`:
- `testDoctorProfileAndWeeklySchedule_multiClinicSeparation`: **PASSED**.
- `testPatientBookingJourney_endToEndPrescriptionAndTokenAllocation`: **PASSED**.
- `testTokenAllocation_capacityLimit_10thSucceeds_11thRejectsOrFallsBack`: **PASSED**.
- `testCancellation_freesCapacitySlot`: **PASSED**.
