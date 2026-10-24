# Android Telephony Feasibility & Bangladesh Carrier Compatibility Notes

**Last Verified:** 2026-10-24

---

## 1. Fundamental Physical & Platform Reality (Phone-Off Forwarding)

- **No Execution When Powered Off:** An Android application cannot execute code, monitor broadcasts, run `WorkManager` jobs, or transmit cellular signalling once the device is powered off or its battery has completely depleted.
- **Carrier-Handled `CFNRc` (`62`):** Call Forwarding When Unreachable (*Call Forward Not Reachable*, 3GPP TS 22.030 Service Code `62`) is stored in the **mobile operator's core network** (HLR / HSS / IMS TAS). When configured **in advance** while the phone still has battery and cellular service, the operator's network automatically redirects incoming voice calls whenever the handset becomes powered off, out of coverage, or unreachable.
- **Persistence Across Reboots:** Because forwarding rules live on the carrier network, rebooting the phone, force-stopping Callora, or even uninstalling the app **does not** automatically cancel active carrier-side call forwarding. Callora explicitly warns users of this fact and provides guided status checks and restoration flows.

---

## 2. Official Android SDK API Evaluation (`minSdk = 26`, `targetSdk = 35`)

| Android API / Mechanism | SDK Level | Required Permission / Role | Third-Party App Availability & Limitations | Callora Strategy |
|---|---|---|---|---|
| `TelephonyManager.setCallForwarding` / `CallForwardingInfo` | API 31+ (`@SystemApi`) | `MODIFY_PHONE_STATE` (`signature\|privileged`) | **Unavailable** to ordinary Play Store / APK-distributed apps. | Documented as privileged-only; never called via hidden API reflection. |
| `SubscriptionManager.getActiveSubscriptionInfoList()` | API 22+ | `READ_PHONE_STATE` | **Available.** Returns active physical SIMs & eSIMs (`SubscriptionInfo`). Does not guarantee phone number availability (API 33+ requires `READ_PHONE_NUMBERS` for `getPhoneNumber`). | Used for dynamic multi-SIM discovery; phone numbers are optional and masked if present. Graceful manual fallback if permission denied. |
| `TelephonyManager.createForSubscriptionId(subId).sendUssdRequest(...)` | API 26+ | `CALL_PHONE` | **Conditionally available with major caveats:** (1) Some OEM/IMS stacks reject 3GPP Supplementary Service MMI codes (`*#62#`, `**62*...#`) over `sendUssdRequest` with `-1`/`-2`; (2) **Android 17 QPR2+** restricts call-forwarding codes via `sendUssdRequest` for non-exempt apps, returning `USSD_ERROR_NOT_ALLOWED` to prevent background call-diversion fraud. | Offered only as an optional, user-initiated direct check/attempt with explicit permission rationale; gracefully catches `SecurityException`, `-1`, `-2`, and `USSD_ERROR_NOT_ALLOWED`, falling back immediately to `ACTION_DIAL` or System Call Settings. |
| `Intent.ACTION_DIAL` with `Uri.fromParts("tel", mmi, null)` | API 1+ | **None** | **Universally available.** Pre-fills the system dialer so the user sees the exact MMI code (and OS confirmation prompt on Android 17 QPR2+) and manually taps Call. Caveats: (1) Some third-party dialers strip `#`; (2) `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE` is a hint that not all dual-SIM OEM dialers enforce automatically. | **Primary safe execution pathway.** Encodes `#` via `Uri.fromParts`, supplies `EXTRA_PHONE_ACCOUNT_HANDLE` where resolvable, warns the user to confirm the target SIM button in the dialer, provides 1-tap *Copy MMI Code*, and marks result `COMMAND_ACCEPTED_UNVERIFIED` / `COMMAND_SUBMITTED` rather than `SUCCESS_VERIFIED`. |
| `TelecomManager.ACTION_SHOW_CALL_SETTINGS` | API 21+ | **None** | **Available.** Opens the system Phone app's Call Settings where the privileged system UI queries/updates `CallForwardingInfo` over IMS UT/XCAP or CS SS. | Offered on every SIM card and fallback sheet, and prioritized for carriers whose official documentation specifies handset Call Settings (e.g., Teletalk). |

---

## 3. Bangladesh Mobile Operator Compatibility Matrix

### Mobile Number Portability (MNP) Notice
Bangladesh launched nationwide MNP in October 2018. Number prefixes (`013`, `014`, `015`, `016`, `017`, `018`, `019`) identify only the **original BTRC number-block allocation**, not necessarily the subscriber's current serving carrier. Callora identifies inserted SIM carriers primarily via `MCC/MNC` (`470-xx`) and `carrierId`, and warns users not to rely solely on destination prefixes for on-net tariff assumptions.

### Operator Evidence Summary

| Operator | MCC-MNC | Original BTRC Prefixes | Forwarding Codes & Mechanism | Verification Level & Documented Source | Documented Tariff / Operational Notes |
|---|---|---|---|---|---|
| **Grameenphone (GP)** | `470-01` | `017`, `013` | `CFNRc`: `**62*<num>#` / `##62#` / `*#62#`<br/>`CFB`: `**67*<num>#` / `##67#` / `*#67#`<br/>`CFNRy`: `**61*<num>#` / `##61#` / `*#61#`<br/>`CFU`: `**21*<num>#` / `##21#` / `*#21#`<br/>Cancel All: `##002#` | **Officially Published** on Grameenphone *Useful Information — GSM Feature Activation/Deactivation* (`grameenphone.com/useful-information`). | Official GP offer terms explicitly state that **FlexiPlan minutes, purchased minute packs, bonus minutes, special call-rate offers, and Emergency Balance minute loans are NOT applicable** to call divert/forward calls; regular base package tariff + VAT/SD/SC applies. |
| **Robi Axiata** | `470-02` | `018` (and merged `016`) | Standard 3GPP TS 22.030 GSM MMI (`**62*`, `**67*`, `**61*`, `**21*`, `##62#`, `*#62#`, `##002#`) + Android Phone Call Settings. | **Standard 3GPP GSM MMI (Plan/IMS Dependent).** Robi operates standard GSM/UMTS/VoLTE supplementary services; helpline `121`. | Diverted calls are billed to the forwarding SIM as an outgoing call under operator tariff rules. VoLTE/VoWiFi lines may require IMS UT data/VoLTE active. |
| **Airtel Bangladesh** | `470-07` / `470-02` | `016` | Standard 3GPP TS 22.030 GSM MMI (`**62*`, `**67*`, `**61*`, `**21*`, `##62#`, `*#62#`, `##002#`) + Android Phone Call Settings. | **Standard 3GPP GSM MMI (Merged Robi Axiata Network, Provisioning Dependent).** Helpline `121`. | Merged into Robi Axiata Limited while retaining the Airtel brand (`016` original series). Standard outgoing diversion charges apply. |
| **Banglalink** | `470-03` | `019`, `014` | Standard 3GPP TS 22.030 GSM MMI (`**62*`, `**67*`, `**61*`, `**21*`, `##62#`, `*#62#`, `##002#`) + Android Phone Call Settings. | **Standard 3GPP GSM MMI (Provisioning Dependent).** Banglalink Digital Communications Ltd.; helpline `121` / `01911304121`. | Supports VoLTE and Wi-Fi Calling on eligible devices; standard outgoing tariff applies to forwarded call legs. |
| **Teletalk** | `470-04` | `015` | Official guidance specifies **Handset Settings → Call Management → Call Forwarding** (`TelecomManager.ACTION_SHOW_CALL_SETTINGS`); 3GPP GSM MMI codes offered only as manual user-confirmed fallback. | **Official Handset Settings Procedure Published** (`teletalk.com.bd/vas/call-management-services/call-forwardingdiverting`). MMI codes marked *Standard 3GPP Fallback (Unverified on Official Web Table)*. | Teletalk states feature activation carries no subscription fee; standard outgoing call rates apply to forwarded calls. Callora does not auto-execute unverified MMI codes on Teletalk, prioritizing System Call Settings or explicit manual dialer confirmation. |
| **Unknown / Other / Roaming** | Any other | Any | System Call Forwarding Settings (`ACTION_SHOW_CALL_SETTINGS`) + manual operator guidance. | **Unverified Fallback.** | Never auto-executes carrier codes; instructs user to verify roaming forwarding charges (which can incur double international leg billing!). |
