# SehatFlow UAT Report: Multi-Doctor Chambers & Simultaneous Queue Isolation

## 1. Executive Summary & Objective
Modern clinics operate multiple consultation chambers simultaneously (e.g. General Medicine, Pediatrics, Orthopedics). This report validates that SehatFlow supports **multi-chamber concurrency** with strict **queue isolation**:
- Different doctors consult in parallel across dedicated chambers.
- Receptionists can filter and manage queues per chamber.
- Queue actions in one chamber (walk-ins, consultations, call-next) do *not* mutate or interfere with other chambers.

---

## 2. Multi-Chamber Test Architecture
| Chamber | Assigned Doctor | Specialty | Active Session |
|---|---|---|---|
| **Chamber A** | Dr. John (`doc_john`) | General Physician | `sess_demo_1` (XYZ Clinic) |
| **Chamber B** | Dr. Donna (`doc_donna`) | Pediatrics | Parallel OPD Session |
| **Chamber C** | Dr. Alex (`doc_alex`) | Orthopedics | Parallel OPD Session |

---

## 3. Test Cases & Verification Results

### Test Case 1: Chamber Filtering on Receptionist Dashboard
- **Steps**:
  1. Open `ReceptionDashboardScreen`.
  2. The Chamber Filter Toolbar displays:
     - `All Chambers`
     - `Chamber A (Dr. John)`
     - `Chamber B (Dr. Donna)`
     - `Chamber C (Dr. Alex)`
  3. Select `Chamber A (Dr. John)`: List displays only Chamber A patients (e.g. `A-01`, `A-02`, `A-03`).
  4. Select `Chamber B (Dr. Donna)`: List displays only Chamber B pediatric patients (`B-01`, `B-02`).
  5. Select `Chamber C (Dr. Alex)`: List displays only Chamber C orthopedic patients (`C-01`).
- **Result**: **PASSED**.

### Test Case 2: Queue Isolation & Concurrent Operations
- **Steps**:
  1. Record baseline waiting count in Chamber B (e.g. 5 patients).
  2. Register walk-in patient in Chamber A (`displayName = "Walk-in Chamber A"`).
  3. Doctor in Chamber A calls next patient (`DemoDataProvider.callNext()`).
  4. Inspect Chamber B:
     - Chamber B waiting count is completely unchanged (5 patients).
     - Token numbering in Chamber B is untouched.
     - Doctor consultation state in Chamber B is unperturbed.
- **Result**: **PASSED** (100% chamber isolation verified).

### Test Case 3: Token Badging and Visual Distinction
- Every `ReceptionTokenCard` prominently displays the chamber assignment (`Chamber A`, `Chamber B`, `Chamber C`) alongside the appointment date and patient queue position.
- If a patient was rescheduled from another day (e.g. Monday -> Tuesday), an amber banner explicitly notes the capacity fallback origin.
- **Result**: **PASSED**.

---

## 4. Automated Regression Verification
- **Automated Test**: `SehatFlowUatRegressionTest.testMultiDoctorChamberIsolation_chamberA_chamberB_chamberC` — **PASSED**
