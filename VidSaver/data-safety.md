# Data safety declaration — VidSaver

Mapping for the Play Console **Data safety** form. Rows marked *(Phase N)* are
declarations that become true when that phase ships; they are listed now so the
form and the code stay in step.

Privacy policy: <https://ampgames.com/privacy>

## Data collected and shared

| Data type | Collected | Shared | Purpose | Optional | Phase |
| --- | --- | --- | --- | --- | --- |
| Device or other IDs (advertising ID) | Yes | Yes — ad networks | Advertising, analytics | Yes, via UMP consent | 6 |
| App activity — app interactions | Yes | Yes — analytics | Analytics, app functionality | No | 7 |
| App activity — in-app search history | No | No | — | — | — |
| App info and performance — crash logs | Yes | Yes — Crashlytics | Crash diagnostics | No | 7 |
| App info and performance — diagnostics | Yes | Yes — Crashlytics | Performance diagnostics | No | 7 |
| Purchase history | Yes | Yes — RevenueCat | Subscription entitlement | No | 5 |
| Approximate location (IP-derived, by ad networks) | Yes | Yes — ad networks | Advertising | Yes, via UMP consent | 6 |
| Web browsing history | **No** | No | Browsing history and bookmarks stay on the device and are never transmitted. | — | 2 |
| Files and docs | **No** | No | Downloaded videos stay on the device. | — | 3 |
| Personal info (name, email, address) | **No** | No | The app has no account system. | — | — |
| Photos and videos | **No** | No | Not uploaded; saved via MediaStore to the user's device only. | — | 3 |

## Security practices

- Data in transit is encrypted (`usesCleartextTraffic="false"`; all SDK traffic
  is HTTPS).
- Users can request deletion of analytics data via the support email in
  Settings.
- Browsing history, bookmarks and download records are local and are excluded
  from cloud backup and device-to-device transfer (see
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`).
- No data is collected before the UMP consent flow resolves.

## Third-party SDKs that receive data

| SDK | Data | Phase |
| --- | --- | --- |
| Google User Messaging Platform | Consent state | 6 |
| AppLovin MAX and its mediation adapters | Advertising ID, coarse location, ad interactions | 6 |
| Google Ad Manager / AdMob | Advertising ID, ad interactions | 6 |
| RevenueCat | Purchase and entitlement state, anonymous app user ID | 5 |
| Firebase Analytics | App interaction events | 7 |
| Firebase Crashlytics | Crash and diagnostic data | 7 |
| Singular *(optional, off by default)* | Install attribution | 7 |
