# Contributing to Callora

Thank you for contributing to **Callora**! Callora manages real mobile-network call forwarding rules across single-SIM, dual-SIM, and eSIM Android devices, so safety, truthfulness, and privacy are top engineering priorities.

---

## Core Engineering Rules

1. **Never Claim Unverified Carrier State:**
   - Launching `Intent.ACTION_DIAL` or System Call Settings only hands off a command (`COMMAND_ACCEPTED_UNVERIFIED` / `COMMAND_SUBMITTED`).
   - Only mark a rule `NETWORK_STATE_VERIFIED` when authoritative carrier query evidence (`GsmMmiCodec.ParsedCarrierResponse.AuthoritativeStatus`) confirms it.
2. **Multi-SIM Safety First:**
   - Always key SIM operations by `subscriptionId` and `SimSubscription.stableSignature`, never by hardcoded `SIM 1` / `SIM 2` assumptions.
   - Never bypass the multi-SIM user confirmation guard when preparing dialer commands on devices with more than one active subscription.
3. **Zero Network / Minimal Permissions:**
   - Do **not** add `android.permission.INTERNET`, analytics SDKs, crash-reporting SDKs, or `READ_CONTACTS` / `READ_SMS` / `READ_CALL_LOG` / `ACCESS_FINE_LOCATION`.
4. **100% Bilingual Parity (`English` + `বাংলা`):**
   - Never hardcode user-visible strings in Kotlin/Compose UI.
   - Every string added to `core/ui/src/main/res/values/strings.xml` must have an idiomatic Bangladeshi Bengali counterpart in `core/ui/src/main/res/values-bn/strings.xml` with identical positional format specifiers (`%1$s`, `%2$d`, etc.). `LocalizationParityTest` enforces this automatically.
5. **Evidence-Graded Carrier Rules:**
   - When updating `BangladeshCarrierCatalog.kt` or operator docs, distinguish **Official Web Verified** rules from **Standard 3GPP GSM** behavior, and never fabricate carrier codes.

---

## Module Layout

- `:core:model` — Pure Kotlin domain models & enums (zero Android dependencies).
- `:core:common` — Pure Kotlin validation, MMI builder/parser, redaction, state reconciliation, schedule logic, JSON backup codec.
- `:core:telephony` — Carrier catalog, subscription/battery/permission interfaces, fakes, and Android telephony adapters.
- `:core:storage` — Android Keystore encryption, Room database, DataStore preferences, and in-memory test repository.
- `:core:forwarding` — `CalloraForwardingCoordinator`, safe baseline restoration planner, reboot reconciler, and Android executor.
- `:core:ui` — Material 3 theme, reusable components, dialogs, and localized string catalogs.
- `:feature:*` — Feature screens (`:feature:dashboard`, `:feature:sims`, `:feature:profiles`, `:feature:history`, `:feature:help`, `:feature:settings`).
- `:app` — Application container, navigation host, onboarding, background workers, and manifest.

---

## Verification Checklist Before Opening a Pull Request

Run all checks locally and ensure zero failures:

```bash
./gradlew :core:model:test :core:common:test testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug assembleRelease
```

Verify that:
- Phone numbers remain masked in `ConfigurationHistoryEntry` and diagnostic exports (`SensitiveDataRedactor`).
- Imported JSON backups validate schema/fields strictly and never auto-execute carrier commands.
- New UI flows work cleanly in both **English** and **বাংলা**, light and dark themes, and portrait and landscape layouts.
