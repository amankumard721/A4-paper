# A4 Paper Typing — Data safety form update draft

Reviewed: 2 November 2026. Developer: AMAN KUMAR. Package: `com.a4sizepaper.typing.texttopdf`.
Support: AMAKUNAR.IN@GMAIL.COM.

यह guide form भरने के लिए है; Play Console में अभी submit नहीं किया गया है।

## पहले सही release चुनें

Current 1.7.1 debug APK uses Google demo ads. Release builds still default to ADS_ENABLED=false. No new ads-enabled AAB was built or uploaded in this task. The draft below is for an ads-enabled Play release. A local test APK does not establish the behavior of an existing Play listing. The form must match the combined behavior of applicable Play-distributed versions. Scanner metrics also need review even when ads are off.

Installed SDKs: GMA 24.9.0, Meta adapter 6.21.0.1 / Audience Network 6.21.0, UMP 4.0.0, Google Play services ML Kit scanner 16.0.0. Google's current GMA disclosure describes its latest 25.5.0; treat it as baseline guidance, then reconcile with the exact final SDK versions and Meta settings.

## Main questions — ads-enabled release

| Console question | Draft selection / action |
| --- | --- |
| Collect or share required user data types? | Yes |
| Data collected | Yes — SDK off-device technical information counts even without your own server |
| Data shared | Yes — advertising-provider handling must be included |
| Account creation | No; the app has no login/account system |
| Account-deletion URL requirement | Not applicable to account creation; this does not settle the separate data-deletion question |
| Independent security review | No; no qualifying independent audit was obtained |
| Privacy policy URL | https://amankumard721.github.io/privacy-policy/a4-paper-typing/ |

## Data types — Google advertising baseline

For the four rows below, select Collected and Shared for the ads-enabled configuration. Purpose selections follow Google's advertising disclosure: Advertising or marketing; Analytics; Fraud prevention, security, and compliance. ML Kit adds diagnostics/usage analytics but does not itself onward-share the metrics described in its disclosure.

| Category | Data type | Why it applies |
| --- | --- | --- |
| Location | Approximate location | IP-derived area, even without Android location permission |
| Device or other IDs | Device or other IDs | Advertising/app/device and scanner-installation identifiers |
| App activity | App interactions | SDK usage and interaction events |
| App info and performance | Diagnostics | Technical measurements such as latency, loading, device/app configuration and performance |

Do not add Other app performance data merely to repeat Diagnostics. Add a type only if the actual data falls into it.

### Crash logs — resolve before submission

Google documents Android ad-SDK crash reporting for troubleshooting. The app has not added its DISABLE_CRASH_REPORTING opt-out. Include Crash logs where the final SDK's crash reporting is active; Analytics is the troubleshooting purpose. Confirm the exact version's collection/sharing and any Meta crash reporting before finalizing that row. Current generic diagnostic wording alone does not prove every specific crash-log or other-performance category. This draft leaves the final Crash logs shared/purpose configuration unresolved rather than treating every performance checkbox as identical.

## Handling questions for the automatic ad-data rows

- Ephemeral processing: No for retained advertising/measurement information. Do not treat an ad profile as memory-only request processing.
- Required or optional: Required for the current automatic technical collection. The app does not provide every user a switch to disable all SDK data collection. Declining personalized ads alone is not a complete data opt-out. Reassess if implementation changes.
- Select purposes for collection and sharing separately where the Console asks; use actual provider purposes, not unrelated features such as account management.

## Local documents and scanner content

Do not declare editor text, document titles, imported Office/PDF files, photos/signatures or scan images as collected merely because the app reads them locally. There is no automatic content-upload endpoint or document content in ad requests. User-selected Save/Share/Print destinations are separate from automatic advertising transfers and may fall under Play's user-initiated sharing exception. Do not mark contacts, precise location, microphone, payment details or search-history collection based solely on the app's document features.

ML Kit's device/app identifiers, usage events and diagnostics are different from the scan pictures themselves. Its documented metrics add Analytics purposes; they do not turn on-device scan images into uploaded Photos or Files and docs.

## Two answers that still require confirmation

1. **All data encrypted in transit:** Google advertising documents TLS and ML Kit documents HTTPS. Meta 6.21.0's complete off-device transfer behavior has not been verified here. Before selecting Yes for all data, confirm every active SDK path. The loopback-only 127.0.0.1 cache exception is on-device and does not by itself prove either a secure or insecure off-device transfer. Meta may initialize through mediation before an impression, so an unmapped placement is not enough evidence of zero Meta processing.
2. **Can users request data deletion?:** the policy provides an email request path for support correspondence and provider privacy-control links. Select Yes only if that process is actually monitored and appropriate requests are handled. Local file deletion, uninstall and advertising-ID controls are not promises to erase every provider-held record. Do not claim automatic deletion within 90 days without evidence. If Console requests a deletion web page, use a clearly scoped working request process rather than mislabel an account-deletion URL.

## Other App content sections

- Contains ads: Yes when publishing the ads-enabled version; this is separate from Data safety.
- Advertising ID: Yes for the ads-enabled configuration that uses AD_ID. Recheck the final merged AAB manifest and the stated purposes (advertising, measurement/analytics and fraud prevention).
- Target audience: preserve the owner's truthful selection; this document does not invent a child-audience or Families declaration.
- App access: all document tools are accessible without reviewer login credentials.
- Replace old no-ads/no-network/no-data store text when rolling out the corresponding features.

## Remaining release checks

The existing Play artifact was not inspected in Console. Meta's exact additional data types/purposes, all-SDK encryption and the deletion-request operation remain unconfirmed. This is a reviewed draft, not a submitted or certified final declaration. Published privacy policy and binary behavior must agree.

## Official references

- Play form definitions, optional/required, version scope and deletion: https://support.google.com/googleplay/android-developer/answer/10787469
- Google ad-SDK disclosure (current latest version): https://developers.google.com/admob/android/privacy/play-data-disclosure
- Android crash-reporting configuration in Google's platform-SDK guidance: https://developers.google.com/admob/unity/global-settings
- ML Kit technical metrics and handling: https://developers.google.com/ml-kit/android-data-disclosure
- ML Kit input/output stays on-device: https://developers.google.com/ml-kit/terms
- Meta mediation/privacy configuration: https://developers.google.com/admob/android/mediation/meta
- Account-deletion policy scope: https://support.google.com/googleplay/android-developer/answer/13327111
