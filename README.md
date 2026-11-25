# Universal Stream Hub

A native Android entertainment hub that brings movie and TV discovery from many
providers into one app, with legal native playback for free titles, official-app
handoff for subscription services, accounts, synced watchlists and real offline
downloads.

- **Android app:** Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer, Room,
  DataStore, WorkManager, Retrofit + kotlinx.serialization, Coil.
- **Backend:** TypeScript API on Cloudflare Workers with D1 (SQLite), Hono,
  PBKDF2 password hashing, HMAC-SHA256 JWTs, TMDB metadata proxy with caching.
- **CI:** GitHub Actions builds debug and release APKs on every push to `main`
  and publishes them as workflow artifacts and on the `apk-latest` release.

## What genuinely works

| Area | Behaviour |
| --- | --- |
| Discovery | Home rows (trending, popular, new, upcoming, acclaimed, TV) from TMDB when a key is configured, always plus a verified free catalog. |
| Free catalog | Public-domain / Creative Commons films from the Internet Archive, each with a verified direct MP4 stream, poster, runtime and genres. The server resolves this live; the APK also ships a verified 16-film snapshot (`app/src/main/assets/free_catalog.json`) so it is usable before any backend exists. |
| Out of the box | With no hub server reachable the app automatically serves the bundled free catalog for Home, Discover, Search, Details, playback and downloads, and says so in a banner with a link to Profile > Hub server. Nothing is faked; once a server is configured the full TMDB catalogue takes over. |
| Native playback | Media3 ExoPlayer with fullscreen landscape, seek, play/pause, +/-10 s, speed, brightness, volume, subtitle/audio track selection, resume, buffering and error states, and picture-in-picture. |
| Licensed titles | Deep-link handoff to the provider's official Android app (or official website). The app never extracts streams, embeds a browser for playback, or bypasses DRM. |
| Search | Debounced title/person search with filters (type, year, genre, rating, provider, language, sort), recent searches and people results. |
| Accounts | Email + password registration, login, profile, preferences, password recovery (needs an email provider), account deletion. PBKDF2 hashes, JWT sessions. |
| My List | Watchlist, continue-watching with saved position, viewing history. Stored locally in Room and synced to the server when signed in. |
| Recommendations | Built from the user's own watchlist/progress genres and favourite genres. No invented statistics; empty state explains what is missing. |
| Downloads | Real background downloads of the free catalog with progress, storage allowances of 4 / 8 / 16 / 32 / 64 GB or unlimited (enforced before a download starts), a live usage meter, Wi-Fi-only mode and an SD card / USB folder picker via the Storage Access Framework. Downloaded files play in the native player. Licensed DRM titles are rejected with an explanation. |
| Offline/UX | Loading, error and empty states, retry, network error messages, dark cinematic theme, adaptive grids, splash screen, custom icon. |

## Repository layout

```
app/                     Android application (Kotlin + Compose)
  src/main/java/...      app code (data, ui, player, work, util)
  src/main/res/          themes, icons, strings
  src/main/assets/       verified free-catalog snapshot bundled in the APK
  src/test/              JVM unit tests
backend/                 Cloudflare Worker API
  src/                   hono routes, auth, TMDB, catalog, recommendations
  test/                  vitest unit tests
  schema.sql             D1 schema
  wrangler.toml          Worker + D1 binding
docs/                    architecture, setup, API, legal notes
tools/                   free-catalog snapshot generator
.github/workflows/       Android APK + backend CI
```

## Quick start

### 1. Backend

```bash
cd backend
npm install
cp .dev.vars.example .dev.vars      # then edit the values
npx wrangler d1 create universal-stream-hub
# put the returned database_id into wrangler.toml, then:
npx wrangler d1 execute universal-stream-hub --file=schema.sql --remote
npm run dev                         # local API on http://127.0.0.1:8787
```

Required secrets (production: `npx wrangler secret put <NAME>`):

| Secret | Required | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | yes (>= 24 chars) | Signs session tokens |
| `TMDB_API_KEY` **or** `TMDB_READ_TOKEN` | optional | Enables the full multi-provider catalog, people search and provider availability |
| `RESEND_API_KEY` + `MAIL_FROM` | optional | Password recovery email delivery |

Without a TMDB key the API still works and serves the verified free catalog, and
the app shows a clear "TMDB not configured" notice instead of fake data.

You can install and use the APK before deploying anything: it falls back to the
bundled snapshot. Deploy the Worker to unlock TMDB search, provider availability
and cross-device library sync. The snapshot is regenerated with:

```bash
node tools/generate-free-catalog.mjs app/src/main/assets/free_catalog.json
```

That script only keeps titles whose direct MP4 answers HTTP 200 and whose
metadata declares a public-domain or Creative Commons licence.

Get a TMDB key at <https://www.themoviedb.org/settings/api> (free for non-commercial use,
attribution required).

### 2. Android app

```bash
# JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).
# The Gradle 8.9 wrapper is committed, so no global Gradle install is needed.
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:assembleRelease     # release APK (debug-signed unless signing secrets are set)
# or open the project in Android Studio and press Run
```

Point the app at your backend in **Profile > Hub server**, or build with a default:

```bash
./gradlew :app:assembleDebug -PapiBaseUrl=https://your-worker.workers.dev/
```

Emulator tip: a local Worker is reachable at `http://10.0.2.2:8787/`.

## CI and APK

`.github/workflows/android-build.yml` runs on pushes/PRs to `main` and on demand:

1. JDK 17 + Android SDK 35 + build-tools 35.0.0
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:assembleDebug` and `:app:assembleRelease`
4. verifies both APKs exist and that the release APK is signed
5. uploads both APKs as workflow artifacts
6. on `main`, publishes them to the `apk-latest` GitHub release

### Download the APK

- Direct download: <https://github.com/woodjamesley-cyber/UniversalStreamHub-Android/releases/download/apk-latest/app-release.apk>
- Release page (both APKs): <https://github.com/woodjamesley-cyber/UniversalStreamHub-Android/releases/tag/apk-latest>
- Workflow runs and artifacts: <https://github.com/woodjamesley-cyber/UniversalStreamHub-Android/actions>

Only an APK from a green workflow run is a real build. If the latest run is red,
the artifact is missing and the run log shows the compiler error to fix.

Release signing: add repository secrets `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Without
them the release build is signed with the debug key so it still installs.

Install on a device: enable "install unknown apps" for your file manager/browser,
copy the APK over, and install. Minimum Android 7.0 (API 24); target API 35.

## Legal and security

- No scraping, stream extraction, DRM circumvention, or redistribution of
  copyrighted video. See [docs/LEGAL.md](docs/LEGAL.md).
- API credentials live only in Worker secrets - never in the APK or repository.
- Passwords are hashed with PBKDF2-HMAC-SHA256 (150k iterations, per-user salt);
  sessions are signed JWTs; tokens stay in app-private DataStore.
- Downloads are limited to the free public-domain catalog; licensed titles are
  downloaded inside the providers' own apps.

## Docs

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/SETUP.md](docs/SETUP.md) - backend deploy, secrets, TMDB, email
- [docs/API.md](docs/API.md) - REST endpoints
- [docs/LEGAL.md](docs/LEGAL.md) - content policy and provider behaviour

## Licence

MIT (see [LICENSE](LICENSE)). Content and metadata belong to their respective
owners; TMDB attribution is required when the TMDB integration is enabled.
