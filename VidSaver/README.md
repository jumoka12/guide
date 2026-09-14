# VidSaver

An Android in-app browser that detects videos the page already delivers for
playback, downloads them to the device, and plays them back in a built-in
gallery.

> **Phase status:** Phases 1–2 complete (skeleton, browser, video detection,
> download strategies). Phases 3–7 are not implemented yet. See
> [Roadmap](#roadmap).

## Stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 (Material You dynamic color) |
| SDK levels | minSdk 24, targetSdk 35, compileSdk 35 |
| Architecture | Single module, MVVM over `ui/` · `domain/` · `data/` layers |
| DI | Hilt |
| Async | Coroutines + Flow |
| Persistence | Room, DataStore |
| Background work | WorkManager + a foreground service (Phase 3) |
| Media | Media3 Transformer (remux); ExoPlayer playback in Phase 4 |
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

### Adding a new `SiteExtractor`

Extraction is a registry of small strategies. To support a new site:

1. **Write the extractor.** For a site that needs no bespoke parsing, subclass
   `DelegatingSiteExtractor` and declare its domains:
   ```kotlin
   class VimeoExtractor @Inject constructor() :
       DelegatingSiteExtractor("vimeo", listOf("vimeo.com", "player.vimeo.com"))
   ```
   For real parsing, implement `SiteExtractor` directly and override `extract`.
2. **Bind it** in `di/MediaModule.kt`:
   ```kotlin
   @Binds @IntoSet abstract fun bindVimeo(extractor: VimeoExtractor): SiteExtractor
   ```
3. That's it. `ExtractorRegistry` orders extractors itself: `YouTubeBlocker`
   first (so no site extractor can ever claim a blocked domain), then site
   extractors by name, then `GenericExtractor` as the catch-all. An extractor
   that throws falls back to the generic path rather than leaving the user with
   nothing, and every candidate is re-checked against the blocked-domain list on
   the way out.

An extractor must never work around a login wall, paywall, age gate or DRM. If a
page did not deliver the media to the WebView for playback, there is nothing to
extract.

### How video detection works

Three layers feed the candidate list:

1. **Network interception** — `VidSaverWebViewClient.shouldInterceptRequest` sees
   every subresource the page requests. `shouldInterceptRequest` gives the
   *request*, never the response headers, so `MediaSniffer` records URLs that
   already look like media immediately, and for ambiguous ones issues a one-byte
   ranged GET on a background coroutine to read the real `Content-Type`. Probes
   are capped per page and skip obvious static assets.
2. **DOM scanning** — `assets/js/video_sniffer.js` is injected on page finish and
   re-runs on DOM mutation. It reads `<video>`, `<source>` and `og:video`/
   `twitter:player` tags and posts them over the `@JavascriptInterface` bridge.
   It never calls a site API or touches storage.
3. **Extraction** — `ExtractorRegistry` turns the observations into
   `MediaCandidate`s: segments dropped, duplicates merged, sorted best-quality
   first.

`blob:` and `data:` URLs are deliberately ignored: they exist only inside the
page and cannot be re-fetched.

### Download strategies

`DownloadStrategy` has two implementations, chosen by `DownloadStrategySelector`:

- **`DirectFileDownloadStrategy`** — a single HTTP file, resuming with `Range`
  when a partial file exists. If the server ignores the range and replies 200, it
  restarts cleanly rather than appending to a corrupt file.
- **`HlsDownloadStrategy`** — fetches the playlist (following a master playlist
  to the variant matching the requested resolution, best quality by default),
  downloads every segment, concatenates them, then remuxes to MP4 through
  `Remuxer`. `Media3Remuxer` does a lossless container rewrite with
  `Transformer`; if it fails, `PassthroughRemuxer` keeps the MPEG-TS stream so a
  finished download is never thrown away.

Two cases are refused outright rather than worked around:

| Case | Behaviour |
| --- | --- |
| `EXT-X-KEY` with a real method | `DownloadError.Encrypted` — the key is never fetched, the stream is never decrypted |
| No `EXT-X-ENDLIST` (live) | `DownloadError.LiveStream` |

### Ad blocking

`assets/hosts_blocklist.txt` is a **placeholder**; replace it with a real list.
Standard hosts-file syntax, so any public list (StevenBlack, AdAway, Peter Lowe)
drops in unchanged. It is parsed into a `HashSet` off the main thread at startup,
and matching is by host and parent domain, so a 150k-line list costs memory, not
per-request time.

Two safety rules apply at request time: a request to the same host as the page is
never blocked, and a page host on the user's allowlist disables blocking for that
page (the shield button in the browser toolbar).

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
- [x] `usesCleartextTraffic="false"`; WebView file/content access off, Safe Browsing on, third-party cookies off by default
- [x] HLS encryption and live streams refused rather than circumvented
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
| 2 | Browser: WebView, tabs, bookmarks, history, ad-blocker, video sniffing, extractor registry, HLS strategy | Done |
| 3 | Download engine: Room-backed repository, foreground service, resumable/concurrent downloads, MediaStore | Not started |
| 4 | Gallery and Media3 player | Not started |
| 5 | Subscriptions via RevenueCat and the paywall | Not started |
| 6 | Ads: AppLovin MAX + GAM post-bidding, UMP consent, `AdPolicy` | Not started |
| 7 | Analytics, Crashlytics, rate prompt, onboarding, localization, Play readiness | Not started |

## Project layout

```
app/src/main/
├── assets/
│   ├── config/app_config.json        remote-shaped runtime config
│   ├── hosts_blocklist.txt           ad/tracker hosts (PLACEHOLDER)
│   └── js/video_sniffer.js           injected DOM media scanner
├── java/com/ampgames/vidsaver/
│   ├── VidSaverApplication.kt        Hilt entry point, crash-safe startup
│   ├── MainActivity.kt
│   ├── core/                         logging, URL helpers
│   ├── domain/
│   │   ├── browser/                  search engines, unsupported-domain policy
│   │   └── media/                    media models, extractors, HLS parsing
│   ├── data/
│   │   ├── browser/                  Room DAOs, DataStore prefs, ad blocker, sniffer
│   │   ├── config/                   AppConfig model + loader
│   │   └── download/                 DownloadStrategy implementations
│   ├── di/                           Hilt modules
│   └── ui/                           theme, navigation, one package per tab
└── res/
```
