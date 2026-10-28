# SehatFlow: Secure Google Pay UPI Integration & Secret Isolation for GitHub

Configure the user's Google Pay UPI credentials (`aakash.bhkta32-1@okicici`, `Aakash Bhakta`, `9915110640`) strictly inside `.env` (gitignored), injected securely into the Android client via the Secrets Gradle Plugin and `BuildConfig`, ensuring zero personal credentials or secrets are leaked when pushing to GitHub.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Strict Secret Protection & Git Isolation (Confirmed):**
> 1. **Secret File in `.gitignore`**:
>    - Your real UPI credentials are saved in `.env` at the project root.
>    - `.env` is already explicitly listed in `.gitignore` (Line 30: `.env`, Line 31: `.env.*`), guaranteeing that `git add` and `git push` will **never** commit or push your personal information to GitHub.
> 2. **Public Template in `.env.example`**:
>    - `.env.example` (tracked in git) contains only sanitized placeholders (`SEHATFLOW_UPI_VPA=yourname@okbank`, `SEHATFLOW_UPI_NAME=Clinic Payee`, etc.).
> 3. **Compilation Injection**:
>    - The Secrets Gradle Plugin reads `.env` locally during build time and injects `SEHATFLOW_UPI_VPA`, `SEHATFLOW_UPI_NAME`, and `SEHATFLOW_UPI_NUMBER` into `BuildConfig`.
> 4. **Runtime Fallback & In-App Settings**:
>    - `SubscriptionPaymentEngine` loads from `BuildConfig` by default.
>    - The in-app Settings dialog retains the editable UPI Configuration card so you can update or customize receiving details at runtime.

- **Confirmed Credential 1: UPI ID (VPA)**: `aakash.bhkta32-1@okicici`
- **Confirmed Credential 2: Registered Payee Name**: `Aakash Bhakta`
- **Confirmed Credential 3: UPI Number**: `9915110640`
- **Confirmed Decision 4: Zero Secret Exposure**: Real credentials isolated strictly in gitignored `.env`.

---

## 1. Overview & Core Concept

- **What It Does**: Wires your verified Google Pay credentials into the SehatFlow Pro subscription billing flow. Clinics tapping "Google Pay", "PhonePe", "Paytm", or scanning the dynamic QR code will transfer funds directly to `aakash.bhkta32-1@okicici` (`Aakash Bhakta`).
- **Target Audience**: Clinic subscribers making payments and you as the repository owner maintaining clean, production-ready code on GitHub.
- **Key Value**: 100% direct bank settlement, zero third-party merchant cuts, zero KYC delays, and total privacy protection when pushing to public or private Git repositories.

---

## 2. User Experience & Visual Design

### Live Payment Flow

1. **Interactive Payment Sheet**:
   - Header: "Subscribe to SehatFlow Pro".
   - Payee Banner: Displays `aakash.bhkta32-1@okicici` (Aakash Bhakta) with a 1-tap copy button.
   - Quick-Pay Launchers:
     - Tapping **Google Pay** launches `com.google.android.apps.nbu.paisa.user` with `pa=aakash.bhkta32-1@okicici&pn=Aakash%20Bhakta&am=499.00&cu=INR&tn=SehatFlow%20Pro`.
     - Tapping **PhonePe**, **Paytm**, or **Any UPI App** launches those apps with the same parameters.
2. **Dynamic QR Code**:
   - High-contrast 29x29 vector QR code encoded with your exact Google Pay VPA.
   - Ideal for clinics paying from another phone or desktop.
3. **Automatic Return & Activation**:
   - Android's `ActivityResultLauncher` captures the return intent upon completion (`Status=SUCCESS`), parses the bank UTR, and activates the subscription immediately.
   - Manual 12-digit UTR input card remains available as a fallback.
4. **Settings Dialog**:
   - In **Settings → Subscription & Billing**, the receiving UPI ID card reflects your credentials with an "Edit" button for on-device adjustments.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Secrets Plugin + `.env` vs. Hardcoded Source Strings**:
  - *Chosen Approach*: Store in `.env` (gitignored) and inject via `BuildConfig`.
  - *Why*: Directly satisfies the user's requirement: personal banking details and phone numbers are excluded from the git tree, protecting your privacy on GitHub.
- **Decision 2: Safe BuildConfig Access**:
  - *Chosen Approach*: Access through `BuildConfig` in `SubscriptionPaymentEngine` with a safe fallback.
  - *Why*: Guarantees that the app builds and tests pass cleanly even in environments where `.env` has not been populated yet.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│      Secret File (Gitignored): `.env`                  │
│  SEHATFLOW_UPI_VPA=aakash.bhkta32-1@okicici            │
│  SEHATFLOW_UPI_NAME=Aakash Bhakta                      │
│  SEHATFLOW_UPI_NUMBER=9915110640                       │
└───────────────────────────┬────────────────────────────┘
                            │ (Secrets Gradle Plugin)
                            ▼
┌────────────────────────────────────────────────────────┐
│             Generated `BuildConfig`                    │
│   BuildConfig.SEHATFLOW_UPI_VPA                        │
│   BuildConfig.SEHATFLOW_UPI_NAME                       │
│   BuildConfig.SEHATFLOW_UPI_NUMBER                     │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             SubscriptionPaymentEngine.kt               │
│   Loads from BuildConfig into default UpiConfig        │
└───────────────┬──────────────────────────┬─────────────┘
                │                          │
                ▼                          ▼
┌──────────────────────────────┐ ┌───────────────────────┐
│ 1-Tap Google Pay Launcher    │ │ Dynamic QR Code       │
│ `upi://pay?pa=aakash...`     │ │ 29x29 Compose Canvas  │
└──────────────────────────────┘ └───────────────────────┘
```

### Component Implementation Steps (Completed & Verified)

1. **Update `.env` (Gitignored)**: [COMPLETED]
   - Added `SEHATFLOW_UPI_VPA=aakash.bhkta32-1@okicici`, `SEHATFLOW_UPI_NAME=Aakash Bhakta`, `SEHATFLOW_UPI_NUMBER=9915110640`.
2. **Update `.env.example` (Git Tracked)**: [COMPLETED]
   - Added sanitized template keys with valid placeholder fallbacks.
3. **Update `SubscriptionPaymentEngine.kt`**: [COMPLETED]
   - Initialized `UpiConfig` dynamically reading from `BuildConfig.SEHATFLOW_UPI_VPA`, `BuildConfig.SEHATFLOW_UPI_NAME`, and `BuildConfig.SEHATFLOW_UPI_NUMBER`.
4. **Subscription UI & Settings Dialog**: [COMPLETED]
   - Updated Clinic Subscription UI and Settings to display receiving Google Pay details, launch UPI intents, and generate dynamic QR codes without hardcoding personal data in source.
5. **Automated Verification**: [VERIFIED]
   - `gradle :app:testDebugUnitTest`: All 66 unit & regression tests passed.
   - `compile_applet`: Clean build succeeded.
   - Zero secrets committed to git-tracked files.
