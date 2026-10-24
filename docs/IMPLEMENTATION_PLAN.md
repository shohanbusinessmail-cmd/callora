# Callora — Master Implementation Plan & Checklist

**Application:** Callora (`com.shohankhan.callora`)  
**Creator / Developer:** Shohan Khan (`helloiamshohan@gmail.com`)  
**Primary Market:** Bangladesh (`+880`, Bengali `বাংলা` & English)  
**Target SDK / Min SDK:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (Android 8.0+)

---

## Phase Checklist

- [x] **Phase 1 — Repository & Feasibility Assessment**
  - [x] Inspect repository state, branches, toolchain, and GitHub permissions.
  - [x] Audit Android SDK telephony APIs (`SubscriptionManager`, `TelephonyManager`, `sendUssdRequest`, `@SystemApi CallForwardingInfo`, `Intent.ACTION_DIAL`, `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE`, Android 17 QPR2 fraud-hardening behavior).
  - [x] Audit Bangladesh carrier documentation (Grameenphone, Robi, Airtel, Banglalink, Teletalk) and document verified vs. standard 3GPP GSM MMI vs. device-dependent behavior.
  - [x] Author `docs/ARCHITECTURE.md` and `docs/FEASIBILITY_AND_TELEPHONY.md`.

- [x] **Phase 2 — Application Foundation**
  - [x] Configure Gradle 8.11.1 wrapper, Kotlin 2.0.21, AGP 8.7.3, KSP, and `gradle/libs.versions.toml` using strictly stable releases.
  - [x] Establish modular Gradle project structure (`:app`, `:core:model`, `:core:common`, `:core:ui`, `:core:telephony`, `:core:forwarding`, `:core:storage`, `:feature:dashboard`, `:feature:sims`, `:feature:profiles`, `:feature:history`, `:feature:settings`, `:feature:help`).
  - [x] Implement Callora Design System (`:core:ui`) with Midnight Navy & Electric Teal Material 3 tokens, dark/light/dynamic themes, Bengali-safe line heights, and non-color-only status indicators.

- [x] **Phase 3 — SIM Discovery & Telephony Abstraction**
  - [x] Implement dynamic multi-SIM discovery (`0`, `1`, `2`, `3+` active physical SIMs & eSIMs) via `SubscriptionManager`.
  - [x] Implement composite `stableSignature` validation to detect SIM swaps, slot reordering, and reboot subscription ID changes.
  - [x] Implement `BangladeshCarrierCatalog` keyed primarily by MCC/MNC (`470-01`, `470-02`, `470-03`, `470-04`, `470-07`) with MNP-aware prefix notes and safe unknown-carrier fallback.
  - [x] Mask subscriber phone numbers by default and avoid requesting unnecessary permissions.

- [x] **Phase 4 — Core Call Forwarding Functionality**
  - [x] Model `CFU` (`21`), `CFB` (`67`), `CFNRy` (`61`), and prioritized `CFNRc` (`62`) explicitly.
  - [x] Implement 3GPP TS 22.030 MMI command builder & USSD response parser (`GsmMmiCodec`).
  - [x] Implement truthful `ForwardingOperationResult` (`SUCCESS_VERIFIED`, `COMMAND_ACCEPTED_UNVERIFIED`, `FAILED`, `UNSUPPORTED`, `PERMISSION_REQUIRED`, `NETWORK_UNAVAILABLE`, `SIM_SELECTION_UNAVAILABLE`, `CANCELLED`).
  - [x] Implement safe `ACTION_DIAL` flow (`Uri.fromParts("tel", mmi, null)` + `EXTRA_PHONE_ACCOUNT_HANDLE` + explicit SIM slot confirmation), System Call Forwarding Settings launcher, and optional subscription-scoped `sendUssdRequest` with Android 17 QPR2 `USSD_ERROR_NOT_ALLOWED` fallback.

- [x] **Phase 5 — Dashboard, SIM Controls, Profiles, History & Restoration**
  - [x] Implement Room + DataStore + Android Keystore (`AES/GCM/NoPadding`) local persistence.
  - [x] Implement built-in templates (*Phone Unreachable*, *Battery Emergency*, *Charging Mode*, *Work Mode*, *Travel Mode*, *Temporary Forwarding*) and full custom profile CRUD + duplication.
  - [x] Implement Main Dashboard, Per-SIM Forwarding Controls, Favorite Destinations, Configuration History, and safe *Restore Intended Forwarding* (never blindly wiping rules with `##002#` without confirmation).

- [x] **Phase 6 — Branding, Launcher Icon & Complete Localization**
  - [x] Generate original Callora brand artwork and build Android Adaptive Icon (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`) plus multi-density legacy PNGs verified inside the 66dp safe zone.
  - [x] Provide complete English (`values/strings.xml`) and Bengali (`values-bn/strings.xml`) localization across all 17 screens/flows, onboarding, and language switching (`AppCompatDelegate.setApplicationLocales`).

- [x] **Phase 7 — Advanced Smart Features & Privacy Maintenance**
  - [x] Implement WorkManager scheduled profile reminders/activation prompts, low-battery threshold monitor & notification, charging-state helper, and `BOOT_COMPLETED` state reconciliation (always requiring user confirmation before changing carrier routing).
  - [x] Implement validated JSON configuration backup/restore (never auto-executing commands on import) and redacted diagnostic export.
  - [x] Implement Troubleshooting Center (10 core failure scenarios), Permissions & Compatibility Inspector, Privacy Policy, and About Callora screen.

- [x] **Phase 8 — Quality Assurance & Automated Verification**
  - [x] Execute unit tests (phone validation/normalization, MMI codec, profile mapping, desired vs observed state reconciliation, backup import/export validation, schedule validation, localization key & format-specifier parity).
  - [x] Execute deterministic fake-telephony integration tests (0/1/2/3+ SIMs, SIM swap, unsupported carrier, network unavailable, permission denial, dialer fallback, unverified vs verified outcomes).
  - [x] Implement Robolectric Compose UI test suite (`CalloraUiRobolectricTest`) and perform static/resource/permission audits.

- [x] **Phase 9 & 10 — CI/CD, Release Preparation & Final Audit**
  - [x] Configure `.github/workflows/ci.yml` and `.github/workflows/release.yml` (zero fake signing keys).
  - [x] Commit, push to working branch, inspect CI/GitHub status, and publish a strictly factual engineering report.
