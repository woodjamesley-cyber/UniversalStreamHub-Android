import { Hono } from "hono";
import { cors } from "hono/cors";
import { hashPassword, requireUser, signToken, verifyPassword, verifyToken } from "./auth";
import { ensureSchema, joinGenres, splitGenres } from "./db";
import type { Env } from "./env";
import { DEFAULT_REGION, TOKEN_TTL_SECONDS, jwtSecret, region } from "./env";
import { HttpError, fail, json, readJson, requireString } from "./http";
import { providerCatalog, providerLinksForNames } from "./providers";
import { buildRecommendations, type TasteSignal } from "./recommend";
import {
  findOpenEntry,
  loadOpenCatalog,
  openNotice,
  openSummaries,
  resolveOpenSources,
  toOpenSummary,
} from "./catalog";
import { detailTmdb, genreMap, homeTmdb, searchTmdb } from "./tmdb";
import type {
  HistoryRow,
  ProgressRow,
  UserRow,
  WatchlistRow,
} from "./db";
import type { MediaSummary, ProviderLink, TitleDetail } from "./types";

const app = new Hono<{ Bindings: Env }>();

app.use(
  "*",
  cors({
    origin: "*",
    allowMethods: ["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allowHeaders: ["Content-Type", "Authorization"],
    maxAge: 86_400,
  }),
);

app.use("*", async (c, next) => {
  await ensureSchema(c.env.DB);
  await next();
});

app.onError((error, c) => {
  if (error instanceof HttpError) return fail(error.status, error.code, error.message);
  console.error("unhandled", error);
  return fail(500, "server_error", "Something went wrong on the hub server.");
});

app.notFound(() => fail(404, "not_found", "That endpoint does not exist."));

const DEMO_ALLOWED_STORAGE = [-1, 2, 4, 8, 16, 32, 64, 128, 256];

interface PreferencesPayload {
  favoriteGenres?: string[];
  region?: string;
  language?: string;
  providerIds?: number[];
  storageLimitGb?: number;
  wifiOnlyDownloads?: boolean;
  autoplayNext?: boolean;
}

interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  avatarColor: string;
  preferences: Record<string, unknown>;
  createdAt: number;
}

function parsePreferences(row: UserRow): Record<string, unknown> {
  try {
    const parsed = JSON.parse(row.preferences) as Record<string, unknown>;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function toUserResponse(row: UserRow): UserResponse {
  return {
    id: row.id,
    email: row.email,
    displayName: row.display_name,
    avatarColor: row.avatar_color,
    preferences: parsePreferences(row),
    createdAt: row.created_at,
  };
}

async function currentUser(request: Request, env: Env): Promise<UserRow> {
  const payload = await requireUser(request, jwtSecret(env));
  const row = await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(payload.sub).first<UserRow>();
  if (!row) throw new HttpError(401, "unauthenticated", "That account no longer exists.");
  return row;
}

function normalisePreferences(input: PreferencesPayload): PreferencesPayload {
  const output: PreferencesPayload = {};
  if (Array.isArray(input.favoriteGenres)) {
    output.favoriteGenres = input.favoriteGenres.filter((genre) => typeof genre === "string").slice(0, 20);
  }
  if (typeof input.region === "string" && /^[A-Za-z]{2}$/.test(input.region)) output.region = input.region.toUpperCase();
  if (typeof input.language === "string" && input.language.length <= 12) output.language = input.language;
  if (Array.isArray(input.providerIds)) {
    output.providerIds = input.providerIds.filter((id) => typeof id === "number").slice(0, 30);
  }
  if (typeof input.storageLimitGb === "number" && DEMO_ALLOWED_STORAGE.includes(input.storageLimitGb)) {
    output.storageLimitGb = input.storageLimitGb;
  }
  if (typeof input.wifiOnlyDownloads === "boolean") output.wifiOnlyDownloads = input.wifiOnlyDownloads;
  if (typeof input.autoplayNext === "boolean") output.autoplayNext = input.autoplayNext;
  return output;
}

app.get("/", (c) =>
  json({
    name: "Universal Stream Hub API",
    version: "1.0.0",
    docs: "https://github.com/woodjamesley-cyber/UniversalStreamHub-Android/blob/main/docs/API.md",
  }),
);

app.get("/health", async (c) => {
  let sources = 0;
  try {
    sources = (await loadOpenCatalog()).length;
  } catch {
    sources = 0;
  }
  return json({
    status: "ok",
    version: "1.0.0",
    tmdbConfigured: Boolean(c.env.TMDB_API_KEY),
    openCatalogSize: sources,
    region: region(c.env),
  });
});

// ---------------------------------------------------------------- catalog ---

app.get("/api/catalog/home", async (c) => {
  const reg = region(c.env, c.req.query("region"));
  const openItems = await openSummaries(20);
  const tmdb = c.env.TMDB_API_KEY ? await homeTmdb(c.env, reg) : { hero: null, rows: [] };

  const rows = [...tmdb.rows];
  if (openItems.length > 0) {
    rows.unshift({ id: "free-to-stream", title: "Free to stream now", items: openItems });
  }
  return json({
    hero: tmdb.hero ?? openItems[0] ?? null,
    rows,
    tmdbConfigured: Boolean(c.env.TMDB_API_KEY),
    openCatalogSize: openItems.length,
    region: reg,
    notice: openNotice(c.env),
  });
});

app.get("/api/catalog/search", async (c) => {
  const reg = region(c.env, c.req.query("region"));
  const query = (c.req.query("q") ?? "").trim();
  const typeParam = (c.req.query("type") ?? "all").toLowerCase();
  const type = typeParam === "movie" || typeParam === "tv" ? typeParam : "all";
  const page = Math.max(1, Number.parseInt(c.req.query("page") ?? "1", 10) || 1);
  const year = c.req.query("year") ? Number.parseInt(c.req.query("year") as string, 10) : null;
  const minRating = c.req.query("minRating") ? Number.parseFloat(c.req.query("minRating") as string) : null;
  const genre = c.req.query("genre") ?? null;
  const provider = c.req.query("provider") ?? null;
  const language = c.req.query("language") ?? null;
  const sort = c.req.query("sort") ?? "popularity.desc";

  if (c.env.TMDB_API_KEY) {
    const outcome = await searchTmdb(c.env, {
      query,
      type,
      page,
      region: reg,
      year: Number.isFinite(year) ? year : null,
      genre,
      minRating: minRating !== null && Number.isFinite(minRating) ? minRating : null,
      provider,
      language,
      sort,
    });
    return json({
      page,
      totalPages: outcome.totalPages,
      totalResults: outcome.totalResults,
      items: outcome.items,
      people: outcome.people,
      tmdbConfigured: true,
      notice: null,
    });
  }

  const open = await openSummaries(64);
  const needle = query.toLowerCase();
  const filtered = open.filter((item) => {
    if (needle && !`${item.title} ${item.genres.join(" ")}`.toLowerCase().includes(needle)) return false;
    if (type === "tv") return false;
    if (year && item.year !== year) return false;
    if (minRating && item.voteAverage < minRating) return false;
    if (genre && !item.genres.some((value) => value.toLowerCase() === genre.toLowerCase())) return false;
    return true;
  });

  return json({
    page,
    totalPages: 1,
    totalResults: filtered.length,
    items: filtered,
    people: [],
    tmdbConfigured: false,
    notice: "TMDB is not configured on this server, so search covers the free public-domain catalog only.",
  });
});

app.get("/api/catalog/genres", async (c) => {
  if (!c.env.TMDB_API_KEY) {
    const open = await openSummaries(48);
    const names = [...new Set(open.flatMap((item) => item.genres))].sort();
    return json({ genres: names.map((name, index) => ({ id: index + 1, name })) });
  }
  const map = await genreMap(c.env);
  return json({ genres: [...map.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name)) });
});

app.get("/api/catalog/providers", (c) =>
  json({ providers: providerCatalog(), region: region(c.env, c.req.query("region")) }),
);

app.get("/api/catalog/open", async (c) => {
  const entries = await loadOpenCatalog();
  return json({ items: entries.map(toOpenSummary), license: "Public domain / Creative Commons Mark 1.0" });
});

async function buildDetail(env: Env, reg: string, id: string): Promise<TitleDetail> {
  if (id.startsWith("open:")) {
    const entry = await findOpenEntry(id);
    if (!entry) throw new HttpError(404, "not_found", "That free title is no longer in the catalog.");
    const media = toOpenSummary(entry);
    const native = await resolveOpenSources(entry.identifier);
    media.quality = native[0]?.quality ?? null;
    media.fileSizeBytes = native[0]?.fileSizeBytes ?? null;
    return {
      media,
      cast: [],
      crew: [],
      trailer: null,
      similar: [],
      seasons: [],
      playback: {
        native,
        providerApps: [],
        notes:
          native.length > 0
            ? [
                "This film is in the public domain and streams natively through Media3 ExoPlayer.",
                "Downloads are enabled for this title because the hub is licensed to store it.",
                `Source: Internet Archive (${entry.identifier}).`,
              ]
            : [
                "The Internet Archive did not return a playable file for this title right now.",
                "Nothing is faked: no player is shown without a real, streamable source.",
              ],
      },
    };
  }

  if (!env.TMDB_API_KEY) {
    throw new HttpError(404, "not_found", "Licensed catalog titles need TMDB_API_KEY on the server.");
  }
  const detail = await detailTmdb(env, id, reg);
  if (!detail) throw new HttpError(404, "not_found", "That title was not found.");
  return detail;
}

app.get("/api/title/:id", async (c) => {
  const detail = await buildDetail(c.env, region(c.env, c.req.query("region")), c.req.param("id"));
  return json(detail);
});

app.get("/api/title/:id/playback", async (c) => {
  const detail = await buildDetail(c.env, region(c.env, c.req.query("region")), c.req.param("id"));
  return json(detail.playback);
});

app.get("/api/downloads/:id", async (c) => {
  const id = c.req.param("id");
  const detail = await buildDetail(c.env, region(c.env), id);
  const source = detail.playback.native.find((entry) => entry.downloadAllowed);
  if (!source) {
    return fail(
      403,
      "not_downloadable",
      "This title is licensed to a streaming provider and can only be downloaded inside that provider's own app.",
    );
  }
  return json({
    id,
    title: detail.media.title,
    url: source.url,
    mimeType: source.mimeType,
    fileSizeBytes: source.fileSizeBytes,
    quality: source.quality,
    license: source.license,
    posterUrl: detail.media.posterUrl,
    downloadable: true,
  });
});

// ------------------------------------------------------------------ auth ---

app.post("/api/auth/register", async (c) => {
  const body = await readJson<{ email?: string; password?: string; displayName?: string }>(c.req.raw);
  const email = requireString(body.email, "email", 160).toLowerCase();
  const password = requireString(body.password, "password", 200);
  const displayName = requireString(body.displayName, "displayName", 60);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new HttpError(400, "invalid_email", "Enter a valid email address.");
  if (password.length < 8) throw new HttpError(400, "weak_password", "Passwords need at least 8 characters.");

  const existing = await c.env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first<{ id: string }>();
  if (existing) throw new HttpError(409, "email_taken", "An account already exists for that email.");

  const id = crypto.randomUUID();
  const now = Date.now();
  const passwordHash = await hashPassword(password);
  const preferences = JSON.stringify({ favoriteGenres: [], region: region(c.env), language: "en-US", storageLimitGb: 8, wifiOnlyDownloads: true, autoplayNext: true });
  await c.env.DB.prepare(
    "INSERT INTO users (id, email, password_hash, display_name, avatar_color, preferences, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
  )
    .bind(id, email, passwordHash, displayName, "#FF4D6D", preferences, now)
    .run();

  const token = await signToken(id, email, jwtSecret(c.env), TOKEN_TTL_SECONDS);
  return json(
    {
      token,
      user: toUserResponse({ id, email, password_hash: passwordHash, display_name: displayName, avatar_color: "#FF4D6D", preferences, created_at: now }),
    },
    201,
  );
});

app.post("/api/auth/login", async (c) => {
  const body = await readJson<{ email?: string; password?: string }>(c.req.raw);
  const email = requireString(body.email, "email", 160).toLowerCase();
  const password = requireString(body.password, "password", 200);
  const row = await c.env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<UserRow>();
  if (!row || !(await verifyPassword(password, row.password_hash))) {
    throw new HttpError(401, "invalid_credentials", "That email and password do not match.");
  }
  const token = await signToken(row.id, row.email, jwtSecret(c.env), TOKEN_TTL_SECONDS);
  return json({ token, user: toUserResponse(row) });
});

app.get("/api/auth/me", async (c) => json({ user: toUserResponse(await currentUser(c.req.raw, c.env)) }));
app.get("/api/me", async (c) => json({ user: toUserResponse(await currentUser(c.req.raw, c.env)) }));

app.patch("/api/me", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  const body = await readJson<{ displayName?: string; avatarColor?: string; preferences?: PreferencesPayload }>(c.req.raw);
  const displayName = typeof body.displayName === "string" && body.displayName.trim().length > 0 ? body.displayName.trim().slice(0, 60) : user.display_name;
  const avatarColor = typeof body.avatarColor === "string" && /^#[0-9a-fA-F]{6}$/.test(body.avatarColor) ? body.avatarColor : user.avatar_color;
  const merged = { ...parsePreferences(user), ...normalisePreferences(body.preferences ?? {}) };
  await c.env.DB.prepare("UPDATE users SET display_name = ?, avatar_color = ?, preferences = ? WHERE id = ?")
    .bind(displayName, avatarColor, JSON.stringify(merged), user.id)
    .run();
  return json({ user: toUserResponse({ ...user, display_name: displayName, avatar_color: avatarColor, preferences: JSON.stringify(merged) }) });
});

app.delete("/api/me", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM watchlist WHERE user_id = ?").bind(user.id),
    c.env.DB.prepare("DELETE FROM progress WHERE user_id = ?").bind(user.id),
    c.env.DB.prepare("DELETE FROM history WHERE user_id = ?").bind(user.id),
    c.env.DB.prepare("DELETE FROM password_resets WHERE user_id = ?").bind(user.id),
    c.env.DB.prepare("DELETE FROM users WHERE id = ?").bind(user.id),
  ]);
  return json({ message: "Your account and synced library were deleted.", delivered: false });
});

app.post("/api/auth/forgot-password", async (c) => {
  const body = await readJson<{ email?: string }>(c.req.raw);
  const email = requireString(body.email, "email", 160).toLowerCase();
  const row = await c.env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<UserRow>();

  if (!row) {
    return json({ message: "If that email has an account, a reset token is on its way.", delivered: false });
  }

  const bytes = crypto.getRandomValues(new Uint8Array(24));
  const token = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  await c.env.DB.prepare("INSERT INTO password_resets (token, user_id, expires_at, used) VALUES (?, ?, ?, 0)")
    .bind(token, row.id, Date.now() + 30 * 60 * 1000)
    .run();

  if (!c.env.RESEND_API_KEY) {
    return fail(503, "email_not_configured", "Password recovery email is not configured on this server. Set RESEND_API_KEY to enable it.");
  }

  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { authorization: `Bearer ${c.env.RESEND_API_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({
        from: c.env.RESEND_FROM ?? "Universal Stream Hub <no-reply@example.com>",
        to: [email],
        subject: "Reset your Universal Stream Hub password",
        text: `Use this token to reset your password: ${token}\nIt expires in 30 minutes.`,
      }),
    });
    return json({
      message: response.ok
        ? "Reset token sent. Check your inbox."
        : "The email service rejected that request. Try again later.",
      delivered: response.ok,
    });
  } catch {
    return fail(502, "email_failed", "The email service is unreachable right now.");
  }
});

app.post("/api/auth/reset-password", async (c) => {
  const body = await readJson<{ token?: string; password?: string }>(c.req.raw);
  const token = requireString(body.token, "token", 120);
  const password = requireString(body.password, "password", 200);
  if (password.length < 8) throw new HttpError(400, "weak_password", "Passwords need at least 8 characters.");

  const row = await c.env.DB.prepare("SELECT * FROM password_resets WHERE token = ? AND used = 0")
    .bind(token)
    .first<{ token: string; user_id: string; expires_at: number }>();
  if (!row || row.expires_at < Date.now()) {
    throw new HttpError(400, "invalid_token", "That reset token is invalid or expired.");
  }

  const passwordHash = await hashPassword(password);
  await c.env.DB.batch([
    c.env.DB.prepare("UPDATE users SET password_hash = ? WHERE id = ?").bind(passwordHash, row.user_id),
    c.env.DB.prepare("UPDATE password_resets SET used = 1 WHERE token = ?").bind(token),
  ]);
  return json({ message: "Password updated. Sign in with your new password.", delivered: true });
});

// --------------------------------------------------------------- library ---

async function libraryPayload(env: Env, userId: string) {
  const [watchlist, progress, history] = await Promise.all([
    env.DB.prepare("SELECT * FROM watchlist WHERE user_id = ? ORDER BY added_at DESC LIMIT 200").bind(userId).all<WatchlistRow>(),
    env.DB.prepare("SELECT * FROM progress WHERE user_id = ? ORDER BY updated_at DESC LIMIT 200").bind(userId).all<ProgressRow>(),
    env.DB.prepare("SELECT * FROM history WHERE user_id = ? ORDER BY viewed_at DESC LIMIT 100").bind(userId).all<HistoryRow>(),
  ]);

  const progressRows = progress.results ?? [];
  const toProgress = (row: ProgressRow) => ({
    id: row.media_id,
    type: row.media_type,
    title: row.title,
    posterUrl: row.poster_url,
    positionMs: row.position_ms,
    durationMs: row.duration_ms,
    progress: row.duration_ms > 0 ? row.position_ms / row.duration_ms : 0,
    updatedAt: row.updated_at,
    completed: row.completed === 1,
  });

  return {
    watchlist: (watchlist.results ?? []).map((row) => ({
      id: row.media_id,
      type: row.media_type,
      title: row.title,
      posterUrl: row.poster_url,
      year: row.year,
      genres: splitGenres(row.genres),
      addedAt: row.added_at,
    })),
    continueWatching: progressRows.filter((row) => row.completed === 0).map(toProgress),
    completed: progressRows.filter((row) => row.completed === 1).map(toProgress),
    recentlyViewed: (history.results ?? []).map((row) => ({
      id: row.media_id,
      type: row.media_type,
      title: row.title,
      posterUrl: row.poster_url,
      viewedAt: row.viewed_at,
    })),
  };
}

interface LibraryRequest {
  id?: string;
  mediaType?: string;
  title?: string;
  posterUrl?: string | null;
  year?: number | null;
  genres?: string[];
  positionMs?: number;
  durationMs?: number;
  completed?: boolean;
}

function cleanGenres(genres: unknown): string {
  if (!Array.isArray(genres)) return "";
  return joinGenres(genres.filter((genre): genre is string => typeof genre === "string").slice(0, 8));
}

app.get("/api/library", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  return json(await libraryPayload(c.env, user.id));
});

app.post("/api/library/watchlist", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  const body = await readJson<LibraryRequest>(c.req.raw);
  const id = requireString(body.id, "id", 120);
  const title = requireString(body.title, "title", 200);
  await c.env.DB.prepare(
    "INSERT OR REPLACE INTO watchlist (user_id, media_id, media_type, title, poster_url, year, genres, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
  )
    .bind(user.id, id, body.mediaType === "tv" ? "tv" : "movie", title, body.posterUrl ?? null, body.year ?? null, cleanGenres(body.genres), Date.now())
    .run();
  return json(await libraryPayload(c.env, user.id));
});

app.delete("/api/library/watchlist/:id", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  await c.env.DB.prepare("DELETE FROM watchlist WHERE user_id = ? AND media_id = ?").bind(user.id, c.req.param("id")).run();
  return json(await libraryPayload(c.env, user.id));
});

app.post("/api/library/progress", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  const body = await readJson<LibraryRequest>(c.req.raw);
  const id = requireString(body.id, "id", 120);
  const title = requireString(body.title, "title", 200);
  const positionMs = Math.max(0, Math.round(body.positionMs ?? 0));
  const durationMs = Math.max(0, Math.round(body.durationMs ?? 0));
  const completed = body.completed === true || (durationMs > 0 && positionMs / durationMs > 0.92);
  const now = Date.now();
  await c.env.DB.batch([
    c.env.DB.prepare(
      "INSERT OR REPLACE INTO progress (user_id, media_id, media_type, title, poster_url, genres, position_ms, duration_ms, completed, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).bind(user.id, id, body.mediaType === "tv" ? "tv" : "movie", title, body.posterUrl ?? null, cleanGenres(body.genres), positionMs, durationMs, completed ? 1 : 0, now),
    c.env.DB.prepare(
      "INSERT OR REPLACE INTO history (user_id, media_id, media_type, title, poster_url, genres, viewed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(user.id, id, body.mediaType === "tv" ? "tv" : "movie", title, body.posterUrl ?? null, cleanGenres(body.genres), now),
  ]);
  return json(await libraryPayload(c.env, user.id));
});

app.post("/api/library/sync", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  const body = await readJson<{ watchlist?: LibraryRequest[]; progress?: LibraryRequest[] }>(c.req.raw);
  const now = Date.now();
  const statements = [];

  for (const item of (body.watchlist ?? []).slice(0, 200)) {
    if (typeof item.id !== "string" || typeof item.title !== "string") continue;
    statements.push(
      c.env.DB.prepare(
        "INSERT OR IGNORE INTO watchlist (user_id, media_id, media_type, title, poster_url, year, genres, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
      ).bind(user.id, item.id.slice(0, 120), item.mediaType === "tv" ? "tv" : "movie", item.title.slice(0, 200), item.posterUrl ?? null, item.year ?? null, cleanGenres(item.genres), now),
    );
  }

  for (const item of (body.progress ?? []).slice(0, 200)) {
    if (typeof item.id !== "string" || typeof item.title !== "string") continue;
    statements.push(
      c.env.DB.prepare(
        "INSERT OR REPLACE INTO progress (user_id, media_id, media_type, title, poster_url, genres, position_ms, duration_ms, completed, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
      ).bind(
        user.id,
        item.id.slice(0, 120),
        item.mediaType === "tv" ? "tv" : "movie",
        item.title.slice(0, 200),
        item.posterUrl ?? null,
        cleanGenres(item.genres),
        Math.max(0, Math.round(item.positionMs ?? 0)),
        Math.max(0, Math.round(item.durationMs ?? 0)),
        item.completed ? 1 : 0,
        now,
      ),
    );
  }

  if (statements.length > 0) await c.env.DB.batch(statements);
  return json(await libraryPayload(c.env, user.id));
});

// -------------------------------------------------------- recommendations ---

async function candidatePool(env: Env): Promise<MediaSummary[]> {
  const open = await openSummaries(24);
  if (!env.TMDB_API_KEY) return open;
  const home = await homeTmdb(env, region(env));
  const merged = new Map<string, MediaSummary>();
  for (const item of [...open, ...home.rows.flatMap((row) => row.items)]) merged.set(item.id, item);
  return [...merged.values()];
}

app.get("/api/recommendations", async (c) => {
  const user = await currentUser(c.req.raw, c.env);
  const [watchlist, history] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM watchlist WHERE user_id = ? ORDER BY added_at DESC").bind(user.id).all<WatchlistRow>(),
    c.env.DB.prepare("SELECT * FROM history WHERE user_id = ? ORDER BY viewed_at DESC LIMIT 50").bind(user.id).all<HistoryRow>(),
  ]);
  const preferences = parsePreferences(user);
  const signal: TasteSignal = {
    favoriteGenres: Array.isArray(preferences.favoriteGenres)
      ? (preferences.favoriteGenres as string[]).filter((genre) => typeof genre === "string")
      : [],
    watchlist: (watchlist.results ?? []).map((row) => ({ id: row.media_id, genres: splitGenres(row.genres) })),
    history: (history.results ?? []).map((row) => ({ id: row.media_id, genres: splitGenres(row.genres) })),
  };
  const pool = await candidatePool(c.env);
  return json(buildRecommendations(pool, signal));
});

app.get("/api/recommendations/guest", async (c) => {
  const genres = (c.req.query("genres") ?? "")
    .split(",")
    .map((genre) => genre.trim())
    .filter((genre) => genre.length > 0);
  const pool = await candidatePool(c.env);
  return json(
    buildRecommendations(pool, { favoriteGenres: genres, watchlist: [], history: [] }, {
      label: genres.length > 0 ? `Because you like ${genres[0]}` : "Recommended for you",
    }),
  );
});

// ------------------------------------------------------------------- me ----

app.patch("/api/users/me", async (c) => c.redirect("/api/me", 307));

app.post("/api/auth/verify", async (c) => {
  const body = await readJson<{ token?: string }>(c.req.raw);
  const token = requireString(body.token, "token", 2000);
  const payload = await verifyToken(token, jwtSecret(c.env));
  if (!payload) throw new HttpError(401, "invalid_token", "That session token is not valid.");
  return json({ valid: true, userId: payload.sub, expiresAt: payload.exp * 1000 });
});

app.get("/api/providers", (c) => json({ providers: providerCatalog(), region: region(c.env, c.req.query("region")) }));

app.all("*", (c) => fail(404, "not_found", `No route for ${c.req.method} ${c.req.path}`));

export default app;
