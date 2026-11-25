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
| Free catalog | 24 public-domain / Creative Commons films from the Internet Archive, each with a verified direct MP4 stream, poster, runtime and genres. This works without any API key. |
| Native playback | Media3 ExoPlayer with fullscreen landscape, seek, play/pause, +/-10 s, speed, brightness, volume, subtitle/audio track selection, resume, buffering and error states, and picture-in-picture. |
| Licensed titles | Deep-link handoff to the provider's official Android app (or official website). The app never extracts streams, embeds a browser for playback, or bypasses DRM. |
| Search | Debounced title/person search with filters (type, year, genre, rating, provider, language, sort), recent searches and people results. |
| Accounts | Email + password registration, login, profile, preferences, password recovery (needs an email provider), account deletion. PBKDF2 hashes, JWT sessions. |
| My List | Watchlist, continue-watching with saved position, viewing history. Stored locally in Room and synced to the server when signed in. |
| Recommendations | Built from the user's own watchlist/progress genres and favourite genres. No invented statistics; empty state explains what is missing. |
| Downloads | Real background downloads of the free catalog with progress, quota choices (4/8/16/32/64 GB or unlimited), Wi-Fi-only mode and an SD card / USB folder picker (SAF). Licensed DRM titles are rejected with an explanation. |
| Offline/UX | Loading, error and empty states, retry, network error messages, dark cinematic theme, adaptive grids, splash screen, custom icon. |

## Repository layout

```
app/                     Android application (Kotlin + Compose)
  src/main/java/...      app code (data, ui, player, work, util)
  src/main/res/          themes, icons, strings
  src/test/              JVM unit tests
backend/                 Cloudflare Worker API
  src/                   hono routes, auth, TMDB, catalog, recommendations
  test/                  vitest unit tests
  schema.sql             D1 schema
  wrangler.toml          Worker + D1 binding
docs/                    architecture, setup, API, legal notes
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

Get a TMDB key at <https://www.themoviedb.org/settings/api> (free for non-commercial use,
attribution required).

### 2. Android app

```bash
# Gradle 8.9+ and JDK 17, with the Android SDK installed
gradle :app:assembleDebug          # debug APK
gradle :app:assembleRelease        # release APK (debug-signed unless signing secrets are set)
# or open the project in Android Studio and press Run
```

Point the app at your backend in **Profile > Hub server**, or build with a default:

```bash
gradle :app:assembleDebug -PapiBaseUrl=https://your-worker.workers.dev/
```

Emulator tip: a local Worker is reachable at `http://10.0.2.2:8787/`.

## CI and APK

`.github/workflows/android-build.yml` runs on pushes/PRs to `main` and on demand:

1. JDK 17 + Android SDK + Gradle 8.9
2. dependency resolution
3. `gradle :app:testDebugUnitTest`
4. `gradle :app:assembleDebug` and `:app:assembleRelease`
5. uploads both APKs as workflow artifacts
6. on `main`, publishes them to the `apk-latest` GitHub release

Download: open the **Actions** tab and pick the newest run's artifacts, or the
**Releases** page for `apk-latest`.

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
