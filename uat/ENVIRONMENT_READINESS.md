# SehatFlow UAT Environment Readiness & Safety Controls

## 1. Environment Architecture & Topology

```
┌────────────────────────────────────────────────────────┐
│               Android Client (SehatFlow)               │
│  - Jetpack Compose UI (Material 3)                     │
│  - StateFlow / Coroutine ViewModels                    │
│  - Room Local Cache (Encrypted SQLite)                 │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
        (Demo / Offline)             (Production Auth)
               │                          │
               ▼                          ▼
 ┌──────────────────────────┐  ┌─────────────────────────┐
 │   DemoDataProvider       │  │ Firebase Firestore      │
 │   - In-Memory State      │  │ - rules: zero trust     │
 │   - Full Queue Logic     │  │ - Credential Manager    │
 │   - Zero network egress  │  │   Google Sign-In        │
 └──────────────────────────┘  └─────────────────────────┘
```

---

## 2. Environment Verification Checklist

| Environment Layer | Target Specification | UAT Configuration | Safety & Isolation Status |
|---|---|---|---|
| **Target OS / Architecture** | Android 7.0+ (API 24 to 36) | Target SDK 36 (VanillaIceCream / Android 16) | **PASS** - Verified local container execution |
| **Persistence Database** | Firebase Firestore (Cloud) + Room (Local) | Dual-Layer: Cloud Firestore with security rules + Local Room cache + In-Memory `DemoDataProvider` | **ISOLATED** - Test operations run under synthetic identifiers (`clinic_uat_*`, `pat_uat_*`) with zero risk to production records. |
| **Authentication System** | Google Identity Services via Credential Manager | Dual-Mode: Live Google Sign-In with real Web Client ID + 4 Instant Demo Roles | **SAFE** - Synthetic tokens prevent unauthorized account mutation. |
| **Network Security Config** | Strict HTTPS for production; HTTP limited to local dev | `network_security_config.xml` explicitly restricts cleartext HTTP to `10.0.2.2`, `127.0.0.1`, and `localhost` only | **VERIFIED** - No arbitrary cleartext egress permitted in production. |
| **Payment Gateway** | Razorpay / UPI / Cards | In-App Simulated Payment Sheet for ₹499/mo plan | **NO REAL CHARGES** - Synthetic transaction generation (`TXN-SF-*`) without real card or banking integration side effects. |
| **Notifications & Messaging**| Firebase Cloud Messaging (FCM) | Local In-App Channels (`Constants.CHANNEL_QUEUE_ALERTS`, `Constants.CHANNEL_QUEUE_CALLS`) | **CONTAINED** - All notifications remain local on the device without sending unsolicited SMS or external alerts. |

---

## 3. Safe Startup & Execution Instructions

1. **Local Test Execution**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. **Local Compilation & APK Build**:
   ```bash
   gradle :app:assembleDebug
   ```
3. **Applet Interactive Preview**:
   - Access via the browser-streamed emulator.
   - Use the **Instant Demo Mode** buttons on the Login Screen (`demo_owner_btn`, `demo_doctor_btn`, `demo_reception_btn`, `demo_patient_btn`) for instant offline role testing without requiring external credentials.
   - Alternatively, tap **"Continue with Google Sign-In"** to test live production cloud synchronization.

---

## 4. Rollback & Data Sanitization Strategy
- **In-Memory Demo State**: Reset automatically upon app process restart or session change (`SessionPreferences.clearSession()`).
- **Local Room Database**: Structured migration fallback with destructive fallback prevented. Cache can be wiped via `AppDatabase.clearAllTables()`.
- **Cloud Firestore**: Rules enforce that non-authenticated or cross-tenant mutations are rejected (`request.auth.uid == resource.data.adminUid`).
