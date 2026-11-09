# Ads setup — A4 Paper Typing 1.7.1 (12)

Updated 2 November 2026. Package: `com.a4sizepaper.typing.texttopdf`.

## Included behavior

Home and Documents have adaptive banner placements. Writing, PDF preview, scanning, import and export do not require watching an ad. No automatic interstitial, app-open or rewarded ads are added. Ad failures leave document functions available.

Dependencies: Google Mobile Ads SDK (legacy) `24.9.0`, Google Meta Audience Network adapter `6.21.0.1`, UMP `4.0.0`. The adapter supplies Meta's SDK transitively. Do not mix the GMA Next-Gen SDK into this dependency graph. This supported GMA 24.x pair matches Meta's tested adapter combination. The newer GMA 25.5.0 artifact contains Kotlin 2.3 metadata and was incompatible with this app's earlier compiler. Kotlin/Compose 2.1.21, KSP 2.1.21-2.0.1 and Hilt 2.56.2 are used with the existing AGP 8.9.1/Gradle 8.12 build. The existing Room 2.6.1 generator is retained with `ksp.useKSP2=false`; switching to KSP2 failed during code generation, so no database-engine or schema migration is introduced for ads. Kotlin's fully tested AGP range for2.1.21 ends at8.7.2; this existing8.9.1 combination is validated by the build checks below rather than claimed as matrix-tested. Google currently lists GMA 24.x as supported, with deprecation 30 June 2027 and sunset 30 June 2028; review that timetable before future releases. [SDK support timetable](https://developers.google.com/admob/android/deprecation). Versions were checked against the [official integration guide](https://developers.google.com/admob/android/mediation/meta).

## Test build

Debug builds use Google's sample IDs:

| Setting | Debug value |
| --- | --- |
| AdMob App ID | `ca-app-pub-3940256099942544~3347511713` |
| Adaptive banner | `ca-app-pub-3940256099942544/9214589741` |

Install the debug APK, connect to the internet and open Home or Documents. UMP checks privacy requirements before ad initialization and requests. If that check cannot authorize requests, ads stay off; Settings provides a retry. There is no debug consent bypass. A sample banner cannot verify your future publisher consent-message configuration.

Google demo units serve Google test ads only: they cannot verify Meta bidding or generate publisher revenue. To test mediation later, use your own units in a dedicated test configuration, register the device with AdMob and enable Meta test mode before requesting ads. Mediated ads may lack Google's Test Ad label; do not click unverified live creatives. [Google test-ad guide](https://developers.google.com/admob/android/test-ads)

## Release configuration

Release ads are disabled by default. Enabling them requires these Gradle properties:

- `adsEnabled=true`
- `admobAppId`: your Android AdMob application ID (contains `~`)
- `admobBannerId`: your Android banner ad unit ID (contains `/`)

Supply them through local Gradle properties or matching `-P` arguments. Do not substitute an interstitial unit. No real publisher IDs are included. Debug builds continue using sample IDs. Signing configuration is separate; ads setup supplies no signing keys. Disabled release ads still bundle these SDKs and network permissions; the app does not initialize the ad SDK or request inventory until enabled.

Complete the account and privacy steps below before enabling release ads. An enabled build alone does not activate accounts or guarantee ad fill.

## AdMob and Meta accounts

1. Add this Android package to AdMob and create a banner unit.
2. In Meta Monetization Manager, create the Android property and a **Banner** placement with Google AdMob as mediation partner.
3. In AdMob's Android banner mediation group, add Meta under bidding, complete the partnership steps, and map the Meta placement ID to the AdMob unit. The placement belongs in this dashboard mapping, not a second hardcoded app ID.
4. Include Meta in the European and US state regulation ad-partner lists in AdMob.
5. Host `app-ads.txt` at the developer website root linked by the Play listing. Use the exact authorized-seller entries from your AdMob and Meta accounts; none have been invented or published here.

Follow the [Meta bidding guide](https://developers.google.com/admob/android/mediation/meta) and [app-ads.txt setup guide](https://support.google.com/admob/answer/9363762).

## Privacy and release declarations

Before ad SDK initialization, Meta Limited Data Use is always requested with `LDU` and country/state `0/0`, delegating applicability to Meta. This does not replace UMP consent, dashboard partner settings or other privacy choices, and does not promise zero collection. Android network security blocks remote cleartext HTTP; the only exception is `127.0.0.1` for Meta's on-device caching proxy. The document WebView continues to block network loads and remote origins.

UMP refreshes consent information at launch, shows required forms, and exposes privacy options when required. Initialization and ad requests wait for `canRequestAds()`; an app-stored Boolean is not a replacement. Publish the appropriate messages for your AdMob application, then test acceptance, rejection and later changes on registered test devices. [UMP guidance](https://developers.google.com/admob/android/privacy)

The app does not send document text, imported files or scanned pages in ad requests. Those remain in the on-device document pipeline. Advertising adds internet access and third-party processing. Google's SDK can transmit IP-derived approximate location, identifiers, app/ad interactions and diagnostics for ads, measurement and fraud prevention. Test ads do not guarantee zero data collection. Include Meta's practices when enabling mediation. [Google SDK disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure), [Meta privacy policy](https://www.facebook.com/privacy/policy/)

Before uploading an ads-enabled build:

- Update the public privacy-policy webpage and in-app policy for the actual SDKs/settings. Remove earlier blanket claims of no ads, no internet or no data collection.
- Review Play Console **Contains ads**, **Advertising ID**, **Data safety**, target audience and content ratings against the final merged manifest and SDK behavior. Do not reuse the earlier no-ads declaration unchanged.
- Confirm the intended audience. Child-directed or mixed-audience settings require separate review if children are included.
- Test privacy changes, offline/no-fill, background/resume, rotation and accidental-click spacing on a physical device.

Play requires accurate ads/privacy declarations and a public privacy-policy page. This implementation does not certify policy or legal compliance. [Play review requirements](https://support.google.com/googleplay/android-developer/answer/9859455)

## Verification status

Debug APK compilation/packaging passed. JVM/Robolectric: 39 tests, 38 passed, 0 failures/errors, 1 skipped Windows symlink test. All 6 new ad-consent gate regressions passed. Final lint passed with 0 errors and 35 warnings. APK signature and 16KB ZIP alignment passed; all 8 native ELF libraries have 16384-byte LOAD alignment. Debug sample IDs and release-disabled BuildConfig were checked. Existing Room schema JSON is byte-for-byte unchanged. Release shrinking/AAB and physical-device ad/consent delivery were not verified. No live account, Meta placement, published publisher consent message, mediation auction or physical-device ad impression has been verified here. The scanner report records checks for version 1.7.0 only.
