export const SCHEMA_STATEMENTS: string[] = [
  `CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    avatar_color TEXT NOT NULL DEFAULT '#FF4D6D',
    preferences TEXT NOT NULL DEFAULT '{}',
    created_at INTEGER NOT NULL
  )`,
  `CREATE TABLE IF NOT EXISTS password_resets (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    used INTEGER NOT NULL DEFAULT 0
  )`,
  `CREATE TABLE IF NOT EXISTS watchlist (
    user_id TEXT NOT NULL,
    media_id TEXT NOT NULL,
    media_type TEXT NOT NULL DEFAULT 'movie',
    title TEXT NOT NULL,
    poster_url TEXT,
    year INTEGER,
    genres TEXT NOT NULL DEFAULT '',
    added_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, media_id)
  )`,
  `CREATE TABLE IF NOT EXISTS progress (
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
  )`,
  `CREATE TABLE IF NOT EXISTS history (
    user_id TEXT NOT NULL,
    media_id TEXT NOT NULL,
    media_type TEXT NOT NULL DEFAULT 'movie',
    title TEXT NOT NULL,
    poster_url TEXT,
    genres TEXT NOT NULL DEFAULT '',
    viewed_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, media_id)
  )`,
  `CREATE TABLE IF NOT EXISTS tmdb_cache (
    cache_key TEXT PRIMARY KEY,
    payload TEXT NOT NULL,
    expires_at INTEGER NOT NULL
  )`,
];

let schemaReady: Promise<void> | null = null;

export async function ensureSchema(db: D1Database): Promise<void> {
  if (!schemaReady) {
    schemaReady = (async () => {
      for (const statement of SCHEMA_STATEMENTS) {
        await db.prepare(statement).run();
      }
    })().catch((error) => {
      schemaReady = null;
      throw error;
    });
  }
  return schemaReady;
}

export const GENRE_SEPARATOR = "\u001F";

export function splitGenres(value: string | null): string[] {
  if (!value) return [];
  return value.split(GENRE_SEPARATOR).filter((genre) => genre.length > 0);
}

export function joinGenres(genres: string[]): string {
  return genres.join(GENRE_SEPARATOR);
}

export interface UserRow {
  id: string;
  email: string;
  password_hash: string;
  display_name: string;
  avatar_color: string;
  preferences: string;
  created_at: number;
}

export interface WatchlistRow {
  media_id: string;
  media_type: string;
  title: string;
  poster_url: string | null;
  year: number | null;
  genres: string;
  added_at: number;
}

export interface ProgressRow {
  media_id: string;
  media_type: string;
  title: string;
  poster_url: string | null;
  genres: string;
  position_ms: number;
  duration_ms: number;
  completed: number;
  updated_at: number;
}

export interface HistoryRow {
  media_id: string;
  media_type: string;
  title: string;
  poster_url: string | null;
  genres: string;
  viewed_at: number;
}
