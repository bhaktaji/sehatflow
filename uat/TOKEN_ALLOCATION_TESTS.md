# SehatFlow UAT Report: Daily Capacity Limits & Automatic Fallback Token Allocation

## 1. Executive Summary & Policy Specification
This document validates the **Daily Capacity Limits, Approval Triage, and Fallback Allocation Engine** in SehatFlow.
Key business constraints validated:
1. **Hard Capacity Enforcement**: The system must never issue an 11th token for a clinic day with configured capacity limit of 10.
2. **Next-Eligible-Day Fallback**: When a patient requests a day that is full (e.g. Monday at XYZ Clinic), the system identifies the next available clinic day for that doctor at the *same* clinic (Tuesday at XYZ Clinic).
3. **Inter-Clinic Isolation**: The system must *never* silently bleed patients across clinics (e.g. assigning a patient who requested XYZ Clinic to ABC Clinic on Wednesday).
4. **Slot Recycling**: Patient cancellations immediately release the capacity slot.

---

## 2. Test Setup & Initial Conditions (Test D)

### Initial Baseline
- **Target Doctor**: Dr. John (`doc_john`)
- **Requested Clinic**: XYZ Clinic
- **Monday Capacity**: 10
- **Monday Approved Count**: 10 (100% Full)
- **Tuesday Capacity**: 10
- **Tuesday Approved Count**: 4 (6 Slots Remaining)

---

## 3. Test Cases & Execution Details

### Case 1: Requested Day Full, Next Day Available (Test D & Test E Case 1)
- **Preconditions**:
  - Monday approved: 10/10.
  - Tuesday approved: 4/10.
- **Workflow Execution**:
  1. Patient submits prescription requesting Monday visit at XYZ Clinic with Dr. John.
  2. Request is recorded in pending verification state (`status = PENDING_VERIFICATION`).
  3. Receptionist opens prescription review sheet (`PrescriptionReviewSheet`).
  4. Sheet displays banner:
     *"⚠️ Notice: Monday at XYZ Clinic is at 100% capacity (10/10). Approval will automatically allocate patient to the next available eligible slot: Tuesday at XYZ Clinic (Chamber A)."*
  5. Receptionist taps **"Approve & Assign Token"**.
  6. Allocation engine executes `allocateTokenWithCapacityCheck`.
  7. Identifies Monday is full (10/10).
  8. Queries Dr. John's weekly schedule for XYZ Clinic.
  9. Identifies Tuesday (Chamber A) as the next eligible day.
  10. Checks Tuesday capacity: 4 approved < 10 capacity (6 remaining).
  11. Generates Token with `appointmentDate = "Tuesday"`, `originalRequestedDate = "Monday"`, `isRescheduled = true`, and `allocationNote = "Requested day Monday was fully booked (10/10). Automatically assigned to next available day: Tuesday."`
  12. Tuesday approved count is incremented to 5.
  13. Monday approved count remains strictly at 10.
- **Result**: **PASSED** (100% compliance with business policy).

### Case 2: Inter-Clinic Bleed Prevention (Test E Case 2)
- **Condition**: Dr. John practices at XYZ Clinic (Mon/Tue) and ABC Clinic (Wed/Thu).
- **Verification**: If Monday and Tuesday at XYZ Clinic are full, the engine does *not* assign to ABC Clinic on Wednesday. It restricts search strictly to the requested clinic or flags capacity exhaustion within the horizon.
- **Result**: **PASSED**.

### Case 3: 11th Token Rejection & Hard Limit Enforcement (Test E Case 9)
- **Condition**: Monday has 10 approved patients. An 11th direct allocation attempt for Monday is initiated.
- **Verification**: The system refuses to allocate an 11th token for Monday. The token is routed to Tuesday. When Tuesday also reaches 10/10, allocation returns `isAllocated = false` and prevents token generation.
- **Result**: **PASSED**.

### Case 4: Cancellation Frees Capacity (Test E Case 8)
- **Preconditions**: Tuesday approved count is 5.
- **Steps**:
  1. Patient calls to cancel token.
  2. `DemoDataProvider.cancelToken(tokenId, "Patient cancellation")` is invoked.
  3. Token status changes to `CANCELLED`.
  4. System decrements approved count for `doc_john|XYZ Clinic|Tuesday` from 5 to 4.
- **Result**: **PASSED**.

---

## 4. Automated Regression Verification
- **Automated Test**: `SehatFlowUatRegressionTest.testCapacityFallbackAllocation_mondayFull_allocatedToTuesdayAtXyzClinic` — **PASSED**
- **Automated Test**: `SehatFlowUatRegressionTest.testCapacityEnforcement_neverIssuesEleventhTokenForDayCapacity10` — **PASSED**
- **Automated Test**: `SehatFlowUatRegressionTest.testCancellation_freesCapacitySlot` — **PASSED**
