# API

The Worker exposes JSON with camelCase fields that match the Android DTOs in `Dtos.kt`.
Errors use `{ "error": { "code", "message" } }`.

Authenticated routes need `Authorization: Bearer <jwt>`.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/health` | Status, version, `tmdbConfigured`, `openCatalogSize` |
| GET | `/api/catalog/home?region=US` | Hero plus rows; free catalog first |
| GET | `/api/catalog/search` | `q,type,page,region,year,genre,minRating,provider,language,sort` |
| GET | `/api/catalog/genres` | Genre list |
| GET | `/api/catalog/providers?region=US` | Provider handoff catalog |
| GET | `/api/catalog/open` | Free public-domain catalog |
| GET | `/api/title/:id?region=US` | Detail with cast, crew, trailer, similar, playback |
| GET | `/api/title/:id/playback` | Playback options only |
| GET | `/api/downloads/:id` | Download authorisation for the free catalog |
| POST | `/api/auth/register` | `{ email, password, displayName }` |
| POST | `/api/auth/login` | `{ email, password }` |
| POST | `/api/auth/forgot-password` | `{ email }`; needs `RESEND_API_KEY` |
| POST | `/api/auth/reset-password` | `{ token, password }` |
| POST | `/api/auth/verify` | `{ token }` |
| GET | `/api/me` | Current user |
| PATCH | `/api/me` | `{ displayName, avatarColor, preferences }` |
| DELETE | `/api/me` | Deletes the account and synced library |
| GET | `/api/library` | Watchlist, continue watching, completed, recently viewed |
| POST | `/api/library/watchlist` | Adds a title |
| DELETE | `/api/library/watchlist/:id` | Removes a title |
| POST | `/api/library/progress` | Saves playback position and history |
| POST | `/api/library/sync` | Merges a device library into the account |
| GET | `/api/recommendations` | Personalised rows from real signals |
| GET | `/api/recommendations/guest?genres=Action,Drama` | Guest rows |

## Media shape

`id` is namespaced: `tmdb:<id>` for licensed catalog titles and `open:<identifier>` for free
Internet Archive titles.

```json
{
  "id": "open:night_of_the_living_dead",
  "source": "open",
  "type": "movie",
  "title": "Night of the Living Dead",
  "playback": "NATIVE",
  "downloadable": true,
  "providers": [],
  "genres": ["Horror"],
  "posterUrl": "https://archive.org/services/img/..."
}
```

`playback` is `NATIVE` only when a real stream was resolved; otherwise it is `PROVIDER_APP` and the
`providerApps` array carries `packageName` and `webUrl` for the official app handoff.

## Preferences

`storageLimitGb` accepts `-1` (unlimited), `2, 4, 8, 16, 32, 64, 128, 256`. Example:

```json
{ "favoriteGenres": ["Horror"], "region": "US", "language": "en-US", "storageLimitGb": 16, "wifiOnlyDownloads": true }
```
