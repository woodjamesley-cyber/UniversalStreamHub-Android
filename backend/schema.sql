-- Universal Stream Hub D1 schema. The Worker also applies this idempotently at runtime.
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  avatar_color TEXT NOT NULL DEFAULT '#FF4D6D',
  preferences TEXT NOT NULL DEFAULT '{}',
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS password_resets (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  used INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS watchlist (
  user_id TEXT NOT NULL,
  media_id TEXT NOT NULL,
  media_type TEXT NOT NULL DEFAULT 'movie',
  title TEXT NOT NULL,
  poster_url TEXT,
  year INTEGER,
  genres TEXT NOT NULL DEFAULT '',
  added_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, media_id)
);

CREATE TABLE IF NOT EXISTS progress (
  user_id TEXT NOT NULL,
  media_id TEXT NOT NULL,
  media_type TEXT NOT NULL DEFAULT 'movie',
  title TEXT NOT NULL,
  poster_url TEXT,
  genres TEXT NOT NULL DEFAULT '',
  position_ms INTEGER NOT NULL DEFAULT 0,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  completed INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, media_id)
);

CREATE TABLE IF NOT EXISTS history (
  user_id TEXT NOT NULL,
  media_id TEXT NOT NULL,
  media_type TEXT NOT NULL DEFAULT 'movie',
  title TEXT NOT NULL,
  poster_url TEXT,
  genres TEXT NOT NULL DEFAULT '',
  viewed_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, media_id)
);

CREATE TABLE IF NOT EXISTS tmdb_cache (
  cache_key TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user ON watchlist(user_id, added_at DESC);
CREATE INDEX IF NOT EXISTS idx_progress_user ON progress(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_history_user ON history(user_id, viewed_at DESC);
