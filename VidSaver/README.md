# VidSaver

An Android in-app browser that detects videos the page already delivers for
playback, downloads them to the device, and plays them back in a built-in
gallery.

> **Phase status:** Phase 1 (skeleton) complete. Phases 2–7 are not implemented
> yet. See [Roadmap](#roadmap).

## Stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 (Material You dynamic color) |
| SDK levels | minSdk 24, targetSdk 35, compileSdk 35 |
| Architecture | Single module, MVVM over `ui/` · `domain/` · `data/` layers |
| DI | Hilt |
| Async | Coroutines + Flow |
| Persistence | Room, DataStore (Phase 2+) |
| Background work | WorkManager + a foreground service (Phase 3) |
| Media | Media3 / ExoPlayer (Phase 4) |
| Networking | OkHttp |
| Images | Coil |
| Logging | Timber |
| Build | Gradle Kotlin DSL + version catalog, R8, ABI splits, App Bundle language splits |

## Setup

1. **Clone and open** `VidSaver/` in Android Studio (Ladybug or newer). Note that
   this directory is a self-contained Gradle build — open `VidSaver/`, not the
   repository root.
2. **Create `local.properties`** from the template:
   ```bash
   cp local.properties.example local.properties
   ```
   Android Studio fills in `sdk.dir` for you. SDK keys may stay empty: a missing
   key compiles to an empty `BuildConfig` string and the matching SDK stays off.
3. **Build and test:**
   ```bash
   ./gradlew assembleDebug
   ./gradlew test                 # JVM unit tests
   ./gradlew connectedAndroidTest # Compose UI tests, needs a device/emulator
   ```
4. **Release signing (optional):** copy `keystore.properties.example` to
   `keystore.properties` and point `storeFile` at your keystore. Without it
   `assembleRelease` still succeeds and produces an unsigned APK.

Neither `local.properties` nor `keystore.properties` is committed — no real key
ever enters the repository.

## Configuration

Every monetization, engagement and browser behaviour is data-driven from
`app/src/main/assets/config/app_config.json`. Nothing about ad cadence, paywall
triggers or blocked domains is hardcoded in feature code.

The file is parsed into [`AppConfig`](app/src/main/java/com/ampgames/vidsaver/data/config/AppConfig.kt)
and provided as a `@Singleton` through Hilt, so any ViewModel or manager can just
take `AppConfig` as a constructor parameter. Parsing is total: a missing or
malformed asset logs an error and falls back to `AppConfig.DEFAULT` rather than
crashing at startup.

### Changing ad placements via config

Edit `assets/config/app_config.json` — no code change needed:

| Key | Effect |
| --- | --- |
| `ads.enabled` | Master switch. `false` makes every ad path a no-op. |
| `ads.launch_route_mode` | `0` never / `1` first session / `2` every launch shows the launch interstitial. |
| `ads.interstitial_capping_minutes` | Minimum minutes between interstitials. |
| `ads.happy_moment_mode` / `happy_moment_capping` | Post-download interstitial and its per-session cap. |
| `ads.mrec_refresh_after_impressions` | Impressions before the browser MREC reloads. |
| `ads.exit_dialog_mode` | `0` plain exit dialog / `1` exit dialog with a native ad. |
| `ads.post_bidding_enabled` | Turns the Google Ad Manager post-bidder on or off. |
| `ads.max_units.*` | AppLovin MAX ad unit IDs. |
| `ads.gam_units.*` | Google Ad Manager ad unit paths used by the post-bidder. |
| `iap.offer_on_resume_every` | Show the paywall on every N-th resume; `0` disables. |
| `engagement.rateus_session_start` | Session number at which the in-app review prompt may fire. |
| `browser.blocked_domains` | Domains the browser refuses to load. |

Ad unit IDs ship as placeholders. Use the AppLovin/AdMob **test** unit IDs during
development; never commit live unit IDs paired with real keys.

### Adding a new `SiteExtractor` (Phase 2)

Extraction is a registry of small strategies. To support a new site:

1. Implement `SiteExtractor`:
   ```kotlin
   class VimeoExtractor @Inject constructor() : SiteExtractor {
       override fun matches(url: String) = url.host().endsWith("vimeo.com")
       override suspend fun extract(
           pageUrl: String,
           html: String,
           sniffed: List<SniffedMedia>,
       ): List<MediaCandidate> = // parse, or delegate to the generic path
   }
   ```
2. Bind it into the registry set in the extractor Hilt module (`@IntoSet`).
3. `ExtractorRegistry` picks the first extractor whose `matches` returns true and
   falls back to `GenericExtractor`, which uses only what the WebView already
   requested.

An extractor must never work around a login wall, paywall, age gate or DRM. If a
page did not deliver the media to the WebView for playback, there is nothing to
extract.

## Compliance

These constraints are requirements, not preferences:

- **No YouTube or Google-owned video domains.** `youtube.com`, `youtu.be`,
  `m.youtube.com` and `music.youtube.com` are blocked in the browser and in the
  extractor layer, and show a "not supported" message. A unit test asserts the
  shipped config blocks all four; `YouTubeBlocker` (Phase 2) enforces it in code
  so the config cannot re-enable them.
- **No DRM, login-wall, paywall or age-gate circumvention.** Only media the page
  already delivered to the WebView for playback is detectable.
- **Scoped storage.** Downloads are written through `MediaStore` to
  `Movies/VidSaver/`. No `WRITE_EXTERNAL_STORAGE` on API 29+.
- **Consent before ads.** Google's User Messaging Platform (UMP) runs first; no
  ad SDK initialises until consent is resolved.
- **No dark patterns on the paywall.** Price, trial length and cancellation terms
  are visible on the paywall screen, and "Continue with free version" is always
  on screen.

### Play policy checklist

- [x] No YouTube / Google-owned video domain support (browser + extractor + test)
- [x] Scoped storage only; no legacy storage permission on API 29+
- [x] Privacy policy and terms links reachable in-app from Settings
- [x] Backup/data-extraction rules exclude all app data
- [x] `usesCleartextTraffic="false"`
- [ ] UMP consent gate before any ad SDK init *(Phase 6)*
- [ ] Notification runtime permission requested in context *(Phase 3)*
- [ ] Foreground service type `dataSync` with a user-visible download notification *(Phase 3)*
- [ ] Paywall shows price, trial length, cancel terms, and a free-tier exit *(Phase 5)*
- [ ] Data safety form matches [`data-safety.md`](data-safety.md) *(Phase 7)*
- [ ] Release build signed from `keystore.properties`, R8 rules verified per SDK

## Data collected

See [`data-safety.md`](data-safety.md) for the Play Console Data safety mapping.
Privacy policy: <https://ampgames.com/privacy>

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Skeleton: Gradle/version catalog, Hilt, Compose, 4-tab navigation, `AppConfig`, Timber, crash-safe Application | Done |
| 2 | Browser: WebView, tabs, bookmarks, history, ad-blocker, video sniffing, extractor registry, HLS strategy | Not started |
| 3 | Download engine: Room-backed repository, foreground service, resumable/concurrent downloads, MediaStore | Not started |
| 4 | Gallery and Media3 player | Not started |
| 5 | Subscriptions via RevenueCat and the paywall | Not started |
| 6 | Ads: AppLovin MAX + GAM post-bidding, UMP consent, `AdPolicy` | Not started |
| 7 | Analytics, Crashlytics, rate prompt, onboarding, localization, Play readiness | Not started |

## Project layout

```
app/src/main/
├── assets/config/app_config.json     remote-shaped runtime config
├── java/com/ampgames/vidsaver/
│   ├── VidSaverApplication.kt        Hilt entry point, crash-safe startup
│   ├── MainActivity.kt
│   ├── core/logging/                 Timber trees
│   ├── data/config/                  AppConfig model + loader
│   ├── di/                           Hilt modules
│   └── ui/                           theme, navigation, one package per tab
└── res/
```
