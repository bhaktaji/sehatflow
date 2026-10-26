# SehatFlow Synthetic Dataset Plan

## 1. Overview & Data Ethics Mandate
In strict accordance with Healthcare Privacy Guidelines and India's **Digital Personal Data Protection (DPDP) Act 2023**:
- **Zero Real Patient Data**: 100% synthetic entities. No real medical records, phone numbers, Aadhaar numbers, or names are ever stored or queried during UAT.
- **Relational Integrity**: All generated appointments, tokens, prescriptions, and consult logs reference valid synthetic doctors, clinics, and patient IDs.

---

## 2. Test Volume Target vs Implemented Dataset

| Entity | Target Volume | Implemented Synthetic Dataset | Relationships & Constraints |
|---|---|---|---|
| **Clinics** | 2–3 | **3 Clinics**: <br>1. *Clinic A: Sehat Care Super Clinic* (Multi-doctor General & Cardiology)<br>2. *Clinic B: Apex Pediatric Care* (Specialist Pediatrics)<br>3. *Clinic C: Greenfield Wellness OPD* (Minimal/Edge Case) | Multi-tenant isolation; unique invite codes (`DOC-XXXX`, `REC-XXXX`) |
| **Doctors** | 6–10 | **8 Synthetic Doctors**:<br>- Dr. Sarah Jenkins (Cardiology, OPD Cabin 1)<br>- Dr. Robert Chen (Pediatrics, OPD Cabin 2)<br>- Dr. Aisha Khan (General Medicine, OPD Cabin 3)<br>- Dr. Vikram Malhotra (Orthopedics, OPD Cabin 4)<br>- Dr. Priya Nair (Gynecology, OPD Cabin 5)<br>- Dr. Amit Verma (ENT, OPD Cabin 6)<br>- Dr. Neha Kapoor (Dermatology, OPD Cabin 7)<br>- Dr. Rajesh Sharma (Diabetology, OPD Cabin 8) | Bound to clinics and queue sessions |
| **Receptionists**| 2–4 | **4 Synthetic Receptionists**:<br>- Sunita Verma (Main Desk 1)<br>- Rahul Khanna (Triage Desk 2)<br>- Meena Kumari (Express Check-in Desk 3)<br>- Ankit Saxena (Emergency Desk 4) | Assigned to walk-in check-in and Rx review |
| **Patients** | 100–200 | **150 Synthetic Patient Profiles**:<br>- Range: Ages 2 to 84<br>- Mix: Walk-in (60%), Digital Prescription (40%)<br>- Names: Diverse Indian names, hyphenated names, non-ASCII characters | Verified DPDP consent state on file |
| **Appointments / Tokens** | 200–400 | **250 Generated Tokens**:<br>- `T-01` to `T-250`<br>- States: 120 Completed, 15 In Consultation, 45 Waiting, 20 Skipped, 30 No-Show, 20 Cancelled | Monotonically increasing numbers per session |
| **Medical / Prescription Records** | 100–300 | **120 Synthetic Prescription Assets**:<br>- Digital mock images/PDF representations<br>- Approved (90), Rejected with reason (15), Pending review (15) | Linked to specific patient and token |

---

## 3. Data Scenarios & Boundary Values

### A. Field Boundary & Internationalization Coverage
1. **Short & Long Names**:
   - Short: "Alok Rao"
   - Long: "Dr. Venkata Ramanujam Sriperumbudur-Chaturvedi" (48 characters)
2. **Special Characters & Hyphens**:
   - "Mary-Jane D'Souza", "Renée O'Connor", "डॉ. अशोक कुमार"
3. **Phone Number Formatting**:
   - 10-digit Indian standard: `9876543210`, with country code `+91 9876543210`
4. **Clinic Address Lengths**:
   - Short: "Cabin 4, Delhi"
   - Long: "Suite 402, Block B, Platinum Towers, Mahatma Gandhi Marg, Sector 62, Noida, Uttar Pradesh 201309"

---

## 4. Lifecycle Progression Matrix

```
[Patient Arrival] ──► Walk-In / Rx Upload ──► [Pending Verification]
                                                        │
                      ┌─────────────────────────────────┴─────────────────┐
                      ▼                                                   ▼
              [Rx Approved]                                       [Rx Rejected]
                      │                                                   │
                      ▼                                                   ▼
                 [Waiting] ◄──────── Reorder / Priority ───► [Terminal: Re-upload]
                      │
                      ▼
               [Token Called] (Audio Chime)
                      │
                      ▼
             [In Consultation]
                      │
         ┌────────────┴─────────────┐
         ▼                          ▼
    [Completed]             [Skipped / No-Show]
```
