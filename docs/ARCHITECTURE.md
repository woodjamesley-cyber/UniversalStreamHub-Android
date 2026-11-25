# Architecture

Universal Stream Hub is two deployables in one repository.

## Android app (`app/`)

Kotlin + Jetpack Compose, single-activity (`MainActivity`) with Compose Navigation and an
optional `PlayerActivity` for native playback.

| Layer | Package | Responsibility |
| --- | --- | --- |
| UI | `ui/` | Screens, shared components, view models, dark theme |
| Playback | `player/` | Media3 ExoPlayer: tracks, speed, resume, PiP, buffering and error states |
| Data | `data/remote` | Retrofit `HubApi`, kotlinx.serialization DTOs, `ApiClient` |
| Data | `data/local` | Room (`AppDatabase`) for library/offline rows, DataStore (`UserStore`) for settings and session |
| Data | `data/repo` | `CatalogRepository`, `AuthRepository`, `LibraryRepository`, `DownloadRepository` |
| Data | `data/bundled` | `BundledFreeCatalog` - verified snapshot in `assets/free_catalog.json`, served when the hub server is unreachable |
| Work | `work/` | `DownloadWorker` (WorkManager) with progress, Wi-Fi gating and quota checks |

Dependency wiring is manual in `AppGraph`; `vmFactory` builds view models without a DI framework.

`LibraryRepository` writes Room first so the app works offline, then mirrors the same operation to
the API when a session exists. On sign-in the local library is reconciled with the server.

## Backend (`backend/`)

Hono on Cloudflare Workers with D1.

| Module | Responsibility |
| --- | --- |
| `index.ts` | Routes, validation, error envelope |
| `auth.ts` | PBKDF2-HMAC-SHA256 (150k iterations) password hashing, HS256 JWT sessions |
| `db.ts` | Idempotent schema bootstrap and row types |
| `tmdb.ts` | TMDB client, genre mapping, DTO mapping, D1 response cache |
| `catalog.ts` | Live Internet Archive public-domain catalog and MP4 resolution |
| `providers.ts` | Official Android packages/sites used for authorised handoff |
| `recommend.ts` | Genre weighting from real user signals |

### Playback policy in code

`buildDetail()` returns `playback.native` only when a real, playable MP4 was resolved from the
Internet Archive. Everything else returns `playback.providerApps` with package names, so the app
hands off to the provider's own application. No DRM, subscription or geo restriction is bypassed.

### Downloads

`GET /api/downloads/:id` returns a source only for titles flagged `downloadAllowed` (the free
catalog). Licensed titles return `403 not_downloadable`. The app enforces the same rule in
`DownloadRepository.enqueue()` and additionally enforces the user's storage allowance
(4/8/16/32/64 GB or unlimited), free disk space, a Wi-Fi-only preference and an optional SAF
folder on removable storage.
