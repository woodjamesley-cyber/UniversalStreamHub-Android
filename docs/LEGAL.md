# Legal and product policy

Universal Stream Hub is a discovery and authorised-playback hub. It is deliberately limited to what
can be done lawfully without breaking anyone's terms of service.

## What the app does

- Aggregates **metadata** about films and shows (TMDB when configured) so users can discover titles
  and see where they are legally available.
- Streams **public-domain / Creative Commons** films natively with Media3 ExoPlayer. These sources are
  resolved live from the Internet Archive together with their licence mark.
- Hands licensed titles off to the **provider's own Android app** through official package and web
  links (Netflix, Disney+, Prime Video, Hulu, Max, Paramount+, Peacock, Apple TV+, Crunchyroll, Tubi,
  Pluto TV, YouTube Movies, The Roku Channel, Plex, Kanopy).
- Lets users keep accounts, watchlists, progress and preferences, and download only the content the
  hub is licensed to store.

## What the app never does

- No scraping of protected streaming sites, no stream extraction from subscription services.
- No DRM circumvention, no paywall bypass, no regional restriction bypass.
- No redistribution of copyrighted films.
- No hidden API keys in the APK: TMDB and email credentials live only in Worker secrets.

## Downloads

Only titles whose licence permits it (public domain / Creative Commons) can be downloaded. The server
refuses others with `403 not_downloadable`, and the app surfaces the provider's own offline option
instead. Subscription services' downloads are managed by their apps and DRM, and are never exported.

## Metadata and trademarks

Provider names, logos and title artwork belong to their owners. Metadata is attributed to TMDB when
configured and to the Internet Archive for free titles. This project is not affiliated with any
streaming provider.

## User data

Passwords are stored as PBKDF2-HMAC-SHA256 hashes with per-user salts. Sessions are HS256 JWTs. Users
can delete their account and synced library from **Profile → Delete account**. Local library data can
be removed by clearing app storage.
