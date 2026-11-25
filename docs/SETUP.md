# Setup

## 1. Backend (Cloudflare Workers + D1)

```bash
cd backend
npm install
npx wrangler d1 create universal-stream-hub      # copy database_id into wrangler.toml
npx wrangler d1 execute universal-stream-hub --file=schema.sql --remote   # optional, the Worker self-migrates
npx wrangler secret put TMDB_API_KEY             # optional: unlocks the licensed catalog
npx wrangler secret put JWT_SECRET               # required for production sessions
npx wrangler secret put RESEND_API_KEY           # optional: password recovery email
npx wrangler secret put RESEND_FROM
npm run dev                                      # local http://127.0.0.1:8787
npm run typecheck && npm test
npx wrangler deploy
```

The Worker applies its schema on the first request, so a fresh database works without a manual
migration. `TMDB_API_KEY` is read only on the server and never shipped in the APK.

### Local development

Copy `.dev.vars.example` to `.dev.vars` and fill in the values you need. Without `TMDB_API_KEY`
the API still serves the live free public-domain catalog from the Internet Archive.

## 2. Android app

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

The project includes the Gradle 8.9 wrapper (`./gradlew`), so only JDK 17 and the Android SDK
are required. You can also open the project in Android Studio and press Run.

### Point the app at your server

1. In the app: **Profile → Hub server**, enter the URL (for example
   `https://universal-stream-hub-api.your-subdomain.workers.dev/`) and tap **Test & save server**.
2. Or build with `-PapiBaseUrl=https://your-worker.workers.dev/`.

### Release signing

Add repository secrets and the workflow signs the release APK with your keystore:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Without those secrets the release build is signed with the debug key so it stays installable.

## 3. Installing the APK

```bash
adb install -r app-release.apk
```

Or open the APK on the device and allow installation from that source. Android 8+ is supported
(`minSdk 24`).

## 4. Storage options for offline downloads

The app offers 4 GB, 8 GB, 16 GB, 32 GB, 64 GB and unlimited allowances
(**Profile → Offline storage**, and on the Downloads screen). A SAF folder picker lets downloads
land on an SD card or USB drive, and Wi-Fi-only mode pauses transfers on mobile data.
