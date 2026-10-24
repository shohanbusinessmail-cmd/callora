# Callora

<p align="center">
  <img src="docs/assets/callora-playstore-512.png" alt="Callora App Icon" width="144" height="144" />
</p>

<p align="center">
  <strong>Smart Android Call Forwarding &amp; Multi-SIM Readiness Manager</strong><br/>
  Built with a Bangladesh-first experience in <strong>বাংলা (Bengali)</strong> and <strong>English</strong>
</p>

---

## Overview

**Callora** (`com.shohankhan.callora`) is a privacy-first, multi-module Android application built with Kotlin and Jetpack Compose (Material 3) that helps single-SIM, dual-SIM, and eSIM users **prepare, verify, organize, and safely restore carrier-level call forwarding** — especially **Forward When Unreachable (`CFNRc`, GSM service code `62`)** before a phone loses battery, enters a dead zone, or is powered off.

Callora is engineered around strict technical honesty:
- **Zero Fake Promises:** An Android app cannot execute code after the phone powers off. Callora helps you configure **network-side operator forwarding in advance** so your mobile carrier redirects incoming calls even when your handset is completely off or out of coverage.
- **Truthful Verification States:** Launching the system dialer or submitting an MMI string is **never** reported as verified carrier activation. Callora separates `NETWORK_STATE_VERIFIED`, `COMMAND_SUBMITTED` (unverified), `UNKNOWN_NETWORK_STATE`, `UNSUPPORTED_CAPABILITY`, and `FAILED`.
- **100% Offline & Local-Only:** Callora requests **no `INTERNET` permission**, embeds zero analytics/ad/crash SDKs, encrypts stored forwarding destination numbers at rest with Android Keystore (`AES-256-GCM`), and disables cloud auto-backup (`android:allowBackup="false"`).

---

## Key Features

- **Unreachable / Phone-Off Readiness (`CFNRc` `62`)**: Dedicated quick preparation, status check (`*#62#`), and baseline-aware restoration.
- **Full 3GPP Call Forwarding Suite**:
  - **When Unreachable (`CFNRc` — `62`)**: Phone powered off, battery depleted, or no signal.
  - **When Busy (`CFB` — `67`)**: Active call in progress or call declined.
  - **When Unanswered (`CFNRy` — `61`)**: Configurable 5–30 second ring timeout (`**61*number**seconds#`).
  - **Always / Unconditional (`CFU` — `21`)**: Immediate redirection with prominent warning banner that handset ringing is bypassed.
- **Dynamic Multi-SIM & eSIM Architecture**:
  - Discovers active physical SIMs and embedded eSIMs dynamically via `SubscriptionManager` and `TelephonyManager.createForSubscriptionId(subId)`.
  - Handles 0, 1, 2, or 3+ active subscriptions, SIM removal, and slot swaps (`stableSignature` reconciliation) without hardcoding two SIM slots.
  - Protects dual-SIM users with an explicit slot-confirmation gate before launching the system dialer.
- **Smart Profiles & Baseline-Safe Restoration**:
  - Built-in templates (*Phone Unreachable*, *Battery Emergency*, *Charging Mode*, *Work Mode*, *Travel Mode*, *Temporary Forwarding*) plus custom profiles, duplication, and per-SIM rules.
  - **Baseline-Aware Restore**: Tracks your normal per-SIM baseline so restoring normal state re-applies your saved baseline or deactivates only rules Callora prepared — never blindly wiping unrelated carrier settings with `##002#`.
  - **WorkManager Readiness Checks**: Deferred low-battery reminders, scheduled profile window prompts (including Bangladesh Sun–Thu workweek presets and overnight windows), and user-confirmed post-reboot reconciliation.
- **Full Bilingual Localization (`বাংলা` & `English`)**:
  - Complete string parity across `values/` and `values-bn/` (`272` localized keys each), Bengali digit (`০–৯`) phone-number normalization, and instant per-app language switching on first launch, TopAppBar, and Settings.

---

## How "Phone-Off" Call Forwarding Really Works

No mobile app on Android or iOS can run background code, intercept calls, or transmit network commands once the device battery reaches `0%` or the OS shuts down.

Instead, GSM/UMTS/LTE/IMS mobile networks support **Call Forwarding on Mobile Subscriber Not Reachable (`CFNRc`, 3GPP TS 22.030 code `62`)**:
1. While your phone **still has power and cellular registration**, you configure `CFNRc` with your mobile operator (for example, `**62*DestinationNumber#` or via System Call Settings).
2. Your operator stores that rule in its core network (HLR/HSS/IMS).
3. Later, if your battery dies, your phone is turned off, or you lose network coverage, the **mobile operator's switch** detects that your SIM is unreachable and routes incoming voice calls to your backup number automatically.

---

## Bangladesh Operator Compatibility (`MCC 470`)

Callora includes an evidence-graded carrier configuration catalog (`BangladeshCarrierCatalog.kt`) separating official operator documentation from standard 3GPP GSM behavior:

| Operator | MCC-MNC | Legacy Prefixes (MNP Caveat*) | MMI Dial Preparation | Verification & Tariff Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Grameenphone** | `470-01` | `017`, `013` | Enabled (`21`, `67`, `61`, `62`, `002`, `*#Code#`) | **Official Web Verified.** Standard package tariff applies to forwarded leg; bundled minute packs, FlexiPlan, bonus minutes, and Emergency Balance do **not** apply. |
| **Robi** | `470-02` | `018`, `016` | Enabled (Standard 3GPP GSM MMI + System Call Settings) | **Standard 3GPP GSM.** Merged Robi-Airtel network; verify active status via `*#62#` or System Call Settings. Helpline: `121`. |
| **Airtel (Robi)** | `470-07` / `470-02` | `016` | Enabled (Standard 3GPP GSM MMI + System Call Settings) | **Standard 3GPP GSM.** Operates on merged Robi Axiata infrastructure; standard outgoing voice tariff applies. Helpline: `121`. |
| **Banglalink** | `470-03` | `019`, `014` | Enabled (Standard 3GPP GSM MMI + System Call Settings) | **Standard 3GPP GSM.** Conditional and unconditional forwarding supported; verify via `*#62#` or System Call Settings. Helpline: `121` / `01911304121`. |
| **Teletalk** | `470-04` | `015` | Routes to **System Call Settings** by default | Official Teletalk VAS documentation confirms handset Call Management Call Forwarding support with free activation, without publishing an official web MMI code table. |
| **Generic / Roaming** | Other | International / Unknown | Routes to **System Call Settings** by default | Safe fallback that avoids guessing unverified operator USSD/MMI dial strings. |

*\*Mobile Number Portability (MNP) has been active in Bangladesh since October 2018. Callora always resolves carriers from active SIM `MCC+MNC` and carrier metadata rather than assuming operator identity from a phone number prefix.*

---

## Supported Android Versions & Execution Path

- **Minimum SDK**: `26` (Android 8.0 Oreo)
- **Target & Compile SDK**: `35` (Android 15)
- **Android 17+ Forward Compatibility**: Android 17 QPR2 blocks call-forwarding service codes in `TelephonyManager.sendUssdRequest()` for third-party apps holding only `CALL_PHONE` (`USSD_ERROR_NOT_ALLOWED`). Callora uses **pre-validated `Intent.ACTION_DIAL` handoff** (with `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE` hint and dual-SIM confirmation) + **System Call Settings** as its primary safe path across Android 8–17+, while treating `sendUssdRequest` strictly as an opt-in secondary path with graceful fallback.

---

## Architecture Overview

Callora uses a clean 13-module Gradle architecture with unidirectional data flow and constructor-injected interfaces (`AppContainer`):

- `:app` — Application entrypoint, adaptive navigation (`NavigationBar` / `NavigationRail`), onboarding, `WorkManager` workers, `BootRecoveryReceiver`, Android manifest & adaptive icons.
- `:core:model` — Pure Kotlin domain models, `ForwardingOperationResult`, `ForwardingVerificationState`, SIM & carrier models.
- `:core:common` — Pure Kotlin phone validator (`PhoneNumberValidator` with BD prefix, Bengali numeral, self-loop, and MMI injection guards), `GsmMmiCodec`, `StateReconciler`, `ScheduleValidator`, `SensitiveDataRedactor`, RFC-8259 `BackupJsonCodec`.
- `:core:telephony` — `BangladeshCarrierCatalog`, `SubscriptionDataSource`, `BatteryStateSource`, `FakeTelephonyPlatform`, and Android `SubscriptionManager`/`TelephonyManager` adapters.
- `:core:storage` — `AndroidKeyStoreCipher` (`AES/GCM/NoPadding`), Room database (`CalloraDatabase`), DataStore preferences, and `InMemoryCalloraStorageRepository`.
- `:core:forwarding` — `CalloraForwardingCoordinator`, `AndroidForwardingExecutor`, `FakeForwardingExecutor`, safe restoration planner, and reboot reconciler.
- `:core:ui` — Midnight Navy & Electric Teal Material 3 theme, Bengali-safe typography, confirmation/progress/result dialogs, and complete `values` + `values-bn` string catalogs.
- `:feature:dashboard`, `:feature:sims`, `:feature:profiles`, `:feature:history`, `:feature:help`, `:feature:settings` — 6 modular Compose feature packages implementing all 17 screens and flows.

Detailed documentation:
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Module graph, Mermaid diagrams, verification state machine, and security architecture.
- [`docs/FEASIBILITY_AND_TELEPHONY.md`](docs/FEASIBILITY_AND_TELEPHONY.md) — Android AOSP telephony API constraints and Bangladesh carrier research.
- [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) — Full local-only privacy policy.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Build, test, localization, and contribution guidelines.

---

## Permissions Justification

| Permission | Required? | Why Callora Uses It |
| :--- | :--- | :--- |
| `READ_PHONE_STATE` | Optional (Recommended) | Discovers active SIM/eSIM slots, carrier names, and `MCC/MNC` so rules target the intended SIM. Without it, manual MMI copy and System Call Settings remain usable. |
| `POST_NOTIFICATIONS` | Optional (Android 13+) | Delivers low-battery CFNRc readiness warnings, scheduled profile reminders, and post-reboot review prompts. |
| `RECEIVE_BOOT_COMPLETED` | Automatic | Checks after device restart whether SIM slots changed while a profile was active and posts a review notification (never executes network commands silently). |
| `CALL_PHONE` | Optional (Off by default) | Used **only** if the user explicitly enables *Attempt Direct USSD When Permitted* in Settings. |

**Permissions Intentionally Omitted:** `INTERNET`, `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALL_LOG`, `READ_SMS`, `SEND_SMS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO`.

---

## Build & Test Instructions

### Prerequisites
- **JDK 17** (or JDK 21)
- **Android SDK Platform 35** & Build-Tools `35.0.0`

### Common Gradle Commands
```bash
# Run all JVM unit, mocked telephony integration, localization parity, and Robolectric UI tests
./gradlew testDebugUnitTest :core:model:test :core:common:test

# Run Android Lint across the application and modules
./gradlew lintDebug

# Build Debug APK
./gradlew assembleDebug

# Build Release APK and Android App Bundle (AAB)
./gradlew assembleRelease bundleRelease
```

### Optional Release Signing (Environment Variables Only)
Callora never commits keystores or signing credentials to Git. To sign release artifacts locally or in CI, provide:
```bash
export CALLORA_KEYSTORE_PATH="/secure/path/release.jks"
export CALLORA_KEYSTORE_PASSWORD="..."
export CALLORA_KEY_ALIAS="..."
export CALLORA_KEY_PASSWORD="..."
./gradlew assembleRelease bundleRelease
```

---

## Known Limitations

1. **System Dialer SIM Picker on Dual-SIM OEMs**: Because `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE` is treated as an advisory hint by some OEM dialers (Xiaomi MIUI/HyperOS, Samsung One UI, Realme/ColorOS), users on dual-SIM devices must verify the highlighted SIM button before pressing Call.
2. **Carrier VoLTE / Supplementary Service Provisioning**: Some SIMs return *"Connection problem or invalid MMI code"* over LTE until VoLTE/Ut supplementary services are provisioned by the carrier or the handset temporarily switches to 3G/2G CS fallback.
3. **Prepaid Balance & Bundled Minutes**: Forwarded calls are billed by mobile operators as outgoing calls from your forwarding SIM; bundled minute packs and emergency balance typically do not cover diverted calls.

---

## Creator & Contact

- **Application**: Callora
- **Creator**: Shohan Khan
- **Contact**: [helloiamshohan@gmail.com](mailto:helloiamshohan@gmail.com)
