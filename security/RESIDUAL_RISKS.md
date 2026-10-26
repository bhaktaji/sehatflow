# SehatFlow Residual Risks & Production Hardening Roadmap

**Application:** SehatFlow  
**Status:** Post-Remediation Assessment  
**Date:** October 2026  

---

## 1. Summary of Residual Risks

All Critical, High, Medium, and Low vulnerabilities discovered in the application codebase and Firestore security rules have been remediated and verified. The following residual risks pertain to external infrastructure, physical hardware compromise on rooted devices, and operational deployment controls.

| Risk ID | Risk Category | Description | Likelihood | Impact | Current Mitigation & Recommended Production Control |
|:---|:---|:---|:---|:---|:---|
| **RR-01** | Rooted Device / Runtime Hook (Frida/Xposed) | A user with a rooted Android device could inspect memory or hook JVM methods on their own device. | Low | Low | Sensitive databases are excluded from ADB backups; server-side Firestore rules enforce zero-trust access regardless of client state. **Recommendation:** Enable Play Integrity API attestation in production release builds. |
| **RR-02** | Certificate Pinning Rotation | Standard CA trust store is enforced via `network_security_config.xml`. Compromised root CAs on enterprise proxies could inspect traffic. | Low | Medium | Cleartext traffic is blocked for all external domains. **Recommendation:** Add SHA-256 SPKI `<pin-set>` in `network_security_config.xml` once production domain certificates are provisioned. |
| **RR-03** | SMS OTP Rate Limiting | Attackers could trigger repeated OTP requests for valid phone numbers (SMS Pumping / DoS). | Medium | Low | Client validates phone format before submission. **Recommendation:** Enforce IP and phone-number rate limiting via Cloud Armor / Firebase App Check on the backend OTP gateway. |
| **RR-04** | Offline SQLite At-Rest Encryption | Room database (`clinic_queue_local.db`) relies on Android OS file-based encryption (FBE) and sandbox isolation. | Low | Low | Excluded from ADB and cloud backups. **Recommendation:** If storing extended historical clinical notes offline, integrate SQLCipher for application-layer encryption. |

---

## 2. Compliance Sign-Off Matrix

| Regulatory Framework | Control Area | Status | Notes |
|:---|:---|:---|:---|
| **DPDP Act 2023 (India)** | Consent & Purpose Limitation | **COMPLIANT** | Explicit patient consent flag (`consentAccepted`) enforced on prescription uploads. |
| **DPDP Act 2023 (India)** | Reasonable Security Safeguards | **COMPLIANT** | Zero-trust Firestore rules, TLS enforcement, and log redaction active. |
| **HIPAA Security Rule** | Access Control (§164.312(a)(1)) | **COMPLIANT** | Unique user identification, role-based access, and strict BOLA isolation. |
| **HIPAA Security Rule** | Transmission Security (§164.312(e)(1)) | **COMPLIANT** | Cleartext HTTP disabled for remote endpoints; auth headers redacted. |
| **Google Play Policy** | Permissions & Photo Picker | **COMPLIANT** | Zero broad storage permissions requested; uses scoped URI access. |
