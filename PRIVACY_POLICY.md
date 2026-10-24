# Privacy Policy for Callora

**Effective Date:** October 24, 2026  
**Application Name:** Callora (`com.shohankhan.callora`)  
**Creator:** Shohan Khan  
**Contact Email:** helloiamshohan@gmail.com  

---

## 1. Summary

Callora is an offline, local-only Android utility for preparing and managing carrier-level call forwarding and multi-SIM readiness.

- **No Internet Access:** Callora does **not** request the `android.permission.INTERNET` permission. The app cannot transmit any data to remote servers, cloud services, analytics platforms, or third parties.
- **No Tracking or Ads:** Callora contains zero advertising SDKs, zero analytics trackers, and zero remote crash-reporting libraries.
- **No Unnecessary Personal Data Access:** Callora never requests access to your contacts, call logs, SMS messages, microphone, camera, or location.

---

## 2. Information Processed Locally on Your Device

To provide SIM-aware call forwarding management, Callora processes the following data strictly on your device:

1. **Forwarding Destination Numbers & Profiles:**  
   Phone numbers you enter as forwarding destinations, favorite numbers, smart profiles, and per-SIM baseline configurations are stored locally in Callora's private Room database. Stored destination phone numbers are encrypted at rest using keys generated and protected inside your device's **Android Keystore (`AES-256-GCM`)**.
2. **Active SIM & Carrier Metadata (`READ_PHONE_STATE`):**  
   When you grant Phone State permission, Callora reads active SIM slot indices, subscription identifiers, display labels, carrier names, and Mobile Country/Network Codes (`MCC/MNC`) via Android's `SubscriptionManager` and `TelephonyManager` APIs so commands and guidance match the intended SIM. If Android exposes your SIM's subscriber number, Callora masks it in the UI and audit history (`SensitiveDataRedactor`) rather than storing raw subscriber numbers in logs.
3. **Battery & Charging Status:**  
   Callora reads standard Android battery percentage and charging status broadcasts locally to show low-battery `CFNRc` readiness alerts when enabled.
4. **Local Configuration History:**  
   Callora maintains an on-device history of forwarding actions you prepare or check, storing masked destination numbers, timestamps, SIM labels, and verification states (`Verified`, `Submitted — Unverified`, `Failed`, etc.). You can clear this history at any time inside the app.

---

## 3. How Call Forwarding Commands Interact with Your Mobile Operator

When you choose to activate, deactivate, or check call forwarding:
- Callora either opens the Android **System Dialer** (`Intent.ACTION_DIAL`) with a standard 3GPP GSM MMI code, opens **System Call Settings**, or (only if you explicitly opt in and grant `CALL_PHONE`) invokes Android's `TelephonyManager.sendUssdRequest()`.
- Any MMI/USSD request is transmitted directly over the cellular network between the **Android operating system** and **your mobile network operator** (such as Grameenphone, Robi, Airtel, Banglalink, Teletalk, or your local carrier), subject to your carrier's standard telecommunications policies and tariffs. Callora has no intermediary server.

---

## 4. Backups, Exports, and Cloud Sync

- **Android Cloud Backup Disabled:** Callora sets `android:allowBackup="false"` and excludes local databases and preferences in `data_extraction_rules.xml` and `backup_rules.xml` so encrypted local state and phone numbers are not silently uploaded to cloud backup providers.
- **User-Initiated JSON Backup & Redacted Diagnostics:** You may optionally export a configuration backup JSON file or a redacted diagnostic summary using Android's system file picker (Storage Access Framework) to a location you control. Exported configuration backups contain your saved profile destination numbers so they can be restored; keep exported backup files secure. Redacted diagnostic exports strip and mask phone numbers automatically.

---

## 5. Data Retention and Deletion

All data remains solely on your device until you delete individual profiles/favorites, tap **Clear History** inside Callora, clear Callora's storage in Android Settings, or uninstall the application. Uninstalling Callora permanently removes its local database, preferences, and Android Keystore encryption keys.

---

## 6. Contact

If you have questions or concerns about this Privacy Policy or Callora's privacy practices, contact:

- **Shohan Khan**
- **Email:** [helloiamshohan@gmail.com](mailto:helloiamshohan@gmail.com)
