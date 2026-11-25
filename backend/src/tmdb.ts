import type { Env } from "./env";
import type {
  CastMember,
  CrewMember,
  MediaSummary,
  MediaType,
  ProviderLink,
  SeasonSummary,
  TitleDetail,
} from "./types";
import { linkForProvider } from "./providers";

const BASE = "https://api.themoviedb.org/3";
const IMAGE = "https://image.tmdb.org/t/p/w500";
type Raw = Record<string, unknown>;

function asRaw(value: unknown): Raw {
  return value && typeof value === "object" ? (value as Raw) : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asString(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function tmdbId(id: string): number | null {
  const match = /^tmdb:(\d+)$/.exec(id);
  return match ? Number.parseInt(match[1] as string, 10) : null;
}

async function readCache(env: Env, key: string): Promise<unknown | null> {
  try {
    const row = await env.DB.prepare("SELECT payload, expires_at FROM tmdb_cache WHERE cache_key = ?")
      .bind(key)
      .first<{ payload: string; expires_at: number }>();
    if (!row || row.expires_at < Date.now()) return null;
    return JSON.parse(row.payload) as unknown;
  } catch {
    return null;
  }
}

async function writeCache(env: Env, key: string, payload: unknown, ttlSeconds: number): Promise<void> {
  try {
    await env.DB.prepare(
      "INSERT OR REPLACE INTO tmdb_cache (cache_key, payload, expires_at) VALUES (?, ?, ?)",
    )
      .bind(key, JSON.stringify(payload), Date.now() + ttlSeconds * 1000)
      .run();
  } catch {
    // caching is best effort; a failure must never break a request
  }
}

export async function tmdbGet(
  env: Env,
  path: string,
  params: Record<string, string | number | undefined>,
  ttlSeconds = 1800,
): Promise<Raw | null> {
  if (!env.TMDB_API_KEY) return null;
  const url = new URL(`${BASE}${path}`);
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === "") continue;
    url.searchParams.set(key, String(value));
  }
  url.searchParams.set("api_key", env.TMDB_API_KEY);
  url.searchParams.set("language", "en-US");

  const cacheKey = `${path}?${url.searchParams.toString().replace(env.TMDB_API_KEY, "key")}`;
  const cached = await readCache(env, cacheKey);
  if (cached) return asRaw(cached);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8_000);
  try {
    const response = await fetch(url.toString(), { signal: controller.signal, headers: { accept: "application/json" } });
    if (!response.ok) return null;
    const payload = (await response.json()) as unknown;
    await writeCache(env, cacheKey, payload, ttlSeconds);
    return asRaw(payload);
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

let genreCache: { expires: number; byId: Map<number, string> } | null = null;

export async function genreMap(env: Env): Promise<Map<number, string>> {
  if (genreCache && genreCache.expires > Date.now()) return genreCache.byId;
  const byId = new Map<number, string>();
  for (const path of ["/genre/movie/list", "/genre/tv/list"]) {
    const payload = await tmdbGet(env, path, {}, 86_400);
    for (const entry of asArray(payload?.genres)) {
      const id = asNumber(asRaw(entry).id);
      const name = asString(asRaw(entry).name);
      if (id !== null && name) byId.set(id, name);
    }
  }
  genreCache = { expires: Date.now() + 24 * 60 * 60 * 1000, byId };
  return byId;
}

export function genreIdFor(map: Map<number, string>, name: string): number | null {
  for (const [id, value] of map.entries()) {
    if (value.toLowerCase() === name.toLowerCase()) return id;
  }
  return null;
}

export function mapMedia(raw: Raw, type: MediaType, genres: Map<number, string>, providers: ProviderLink[] = []): MediaSummary {
  const id = asNumber(raw.id) ?? 0;
  const title = asString(raw.title) ?? asString(raw.name) ?? "Untitled";
  const date = asString(raw.release_date) ?? asString(raw.first_air_date);
  const year = date ? Number.parseInt(date.slice(0, 4), 10) : asNumber(raw.year);
  const genreNames = asArray(raw.genre_ids)
    .map((value) => genres.get(asNumber(value) ?? -1))
    .filter((value): value is string => Boolean(value));
  const nestedGenres = asArray(raw.genres)
    .map((value) => asString(asRaw(value).name))
    .filter((value): value is string => Boolean(value));
  const runtime = asNumber(raw.runtime);

  return {
    id: `tmdb:${id}`,
    source: "tmdb",
    type,
    title,
    overview: asString(raw.overview) ?? "",
    posterUrl: asString(raw.poster_path) ? `${IMAGE}${asString(raw.poster_path)}` : null,
    backdropUrl: asString(raw.backdrop_path) ? `${IMAGE}${asString(raw.backdrop_path)}` : null,
    releaseDate: date,
    year: year !== null && Number.isFinite(year) ? year : null,
    voteAverage: asNumber(raw.vote_average) ?? 0,
    genres: genreNames.length > 0 ? genreNames : nestedGenres,
    runtimeMinutes: runtime !== null ? Math.round(runtime) : null,
    providers,
    playback: "PROVIDER_APP",
    downloadable: false,
    license: null,
    quality: null,
    fileSizeBytes: null,
    trailerYoutubeKey: null,
  };
}

export function parseProviders(raw: Raw, region: string): ProviderLink[] {
  const results = asRaw(asRaw(raw["watch/providers"]).results);
  const regional = asRaw(results[region]);
  const links: ProviderLink[] = [];
  const seen = new Set<string>();
  for (const bucket of ["flatrate", "free", "ads", "rent", "buy"]) {
    for (const entry of asArray(regional[bucket])) {
      const item = asRaw(entry);
      const name = asString(item.provider_name);
      if (!name || seen.has(name)) continue;
      const link = linkForProvider(name, asNumber(item.provider_id), asString(item.logo_path));
      if (link) {
        seen.add(name);
        links.push({ ...link, offerType: bucket === "flatrate" ? "subscription" : bucket });
      }
    }
  }
  return links;
}

export interface DiscoverQuery {
  query: string;
  type: "movie" | "tv" | "all";
  page: number;
  region: string;
  year?: number | null;
  genre?: string | null;
  minRating?: number | null;
  provider?: string | null;
  language?: string | null;
  sort?: string | null;
}

export interface SearchOutcome {
  items: MediaSummary[];
  totalPages: number;
  totalResults: number;
  people: Array<{ id: number; name: string; profileUrl: string | null; knownFor: string }>;
}

export async function searchTmdb(env: Env, input: DiscoverQuery): Promise<SearchOutcome> {
  const genres = await genreMap(env);
  const items: MediaSummary[] = [];
  const people: SearchOutcome["people"] = [];
  let totalPages = 1;
  let totalResults = 0;
  const types: MediaType[] = input.type === "all" ? ["movie", "tv"] : [input.type];

  for (const type of types) {
    if (input.query.length > 0) {
      const payload = await tmdbGet(env, `/search/${type}`, { query: input.query, page: input.page, include_adult: "false" });
      for (const entry of asArray(payload?.results)) {
        items.push(mapMedia(asRaw(entry), type, genres));
      }
      totalPages = Math.max(totalPages, asNumber(payload?.total_pages) ?? 1);
      totalResults += asNumber(payload?.total_results) ?? 0;
    } else {
      const params: Record<string, string | number | undefined> = {
        page: input.page,
        sort_by: input.sort ?? "popularity.desc",
        "vote_count.gte": 25,
        watch_region: input.region,
      };
      const genreId = input.genre ? genreIdFor(genres, input.genre) : null;
      if (genreId !== null) params.with_genres = genreId;
      if (input.year) params[type === "movie" ? "primary_release_year" : "first_air_date_year"] = input.year;
      if (input.minRating) params["vote_average.gte"] = input.minRating;
      if (input.language) params.with_original_language = input.language;
      if (input.provider) {
        const providerLink = linkForProvider(input.provider);
        const providerId = providerLink ? findProviderId(providerLink.name) : null;
        if (providerId) params.with_watch_providers = providerId;
      }
      const payload = await tmdbGet(env, `/discover/${type}`, params);
      for (const entry of asArray(payload?.results)) {
        items.push(mapMedia(asRaw(entry), type, genres));
      }
      totalPages = Math.max(totalPages, asNumber(payload?.total_pages) ?? 1);
      totalResults += asNumber(payload?.total_results) ?? 0;
    }
  }

  if (input.query.length > 0 && input.type === "all") {
    const payload = await tmdbGet(env, "/search/person", { query: input.query, page: 1, include_adult: "false" });
    for (const entry of asArray(payload?.results).slice(0, 10)) {
      const person = asRaw(entry);
      const id = asNumber(person.id);
      const name = asString(person.name);
      if (id === null || !name) continue;
      people.push({
        id,
        name,
        profileUrl: asString(person.profile_path) ? `${IMAGE}${asString(person.profile_path)}` : null,
        knownFor: asString(person.known_for_department) ?? "Acting",
      });
    }
  }

  return { items, totalPages, totalResults, people };
}

const PROVIDER_IDS: Record<string, number> = {
  netflix: 8,
  "disney+": 337,
  "prime video": 9,
  hulu: 15,
  max: 1899,
  "paramount+": 531,
  peacock: 386,
  "apple tv+": 350,
  crunchyroll: 283,
  tubi: 73,
  "pluto tv": 300,
  "youtube movies": 192,
  "the roku channel": 328,
  plex: 301,
  kanopy: 1915,
};

function findProviderId(name: string): number | null {
  return PROVIDER_IDS[name.toLowerCase()] ?? null;
}

export async function detailTmdb(env: Env, id: string, region: string): Promise<TitleDetail | null> {
  const numericId = tmdbId(id);
  if (numericId === null) return null;
  const genres = await genreMap(env);

  for (const type of ["movie", "tv"] as MediaType[]) {
    const payload = await tmdbGet(env, `/${type}/${numericId}`, {
      append_to_response: "credits,videos,similar,watch/providers",
    });
    if (!payload || !payload.id) continue;

    const media = mapMedia(payload, type, genres, parseProviders(payload, region));
    const credits = asRaw(payload.credits);
    const cast: CastMember[] = asArray(credits.cast).slice(0, 18).map((entry) => {
      const person = asRaw(entry);
      return {
        name: asString(person.name) ?? "",
        character: asString(person.character),
        profileUrl: asString(person.profile_path) ? `${IMAGE}${asString(person.profile_path)}` : null,
      };
    });
    const crew: CrewMember[] = asArray(credits.crew)
      .map((entry) => asRaw(entry))
      .filter((person) => ["Director", "Writer", "Screenplay", "Producer"].includes(asString(person.job) ?? ""))
      .slice(0, 12)
      .map((person) => ({ name: asString(person.name) ?? "", job: asString(person.job) ?? "" }));

    const videos = asArray(asRaw(payload.videos).results).map((entry) => asRaw(entry));
    const trailerEntry =
      videos.find((video) => asString(video.type) === "Trailer" && asString(video.site) === "YouTube") ??
      videos.find((video) => asString(video.site) === "YouTube");
    const trailerKey = trailerEntry ? asString(trailerEntry.key) : null;
    media.trailerYoutubeKey = trailerKey;

    const similar = asArray(payload.similar ? asRaw(payload.similar).results : undefined)
      .slice(0, 12)
      .map((entry) => mapMedia(asRaw(entry), type, genres));

    const seasons: SeasonSummary[] = asArray(payload.seasons).map((entry) => {
      const season = asRaw(entry);
      return {
        seasonNumber: asNumber(season.season_number) ?? 0,
        name: asString(season.name) ?? "Season",
        episodeCount: asNumber(season.episode_count) ?? 0,
        airDate: asString(season.air_date),
        posterUrl: asString(season.poster_path) ? `${IMAGE}${asString(season.poster_path)}` : null,
      };
    });

    return {
      media,
      cast,
      crew,
      trailer: trailerKey
        ? { name: asString(trailerEntry?.name) ?? "Trailer", youtubeKey: trailerKey, webUrl: `https://www.youtube.com/watch?v=${trailerKey}` }
        : null,
      similar,
      seasons,
      playback: {
        native: [],
        providerApps: media.providers,
        notes: [
          `This title is licensed to ${media.providers.map((link) => link.name).join(", ") || "a subscription provider"}.`,
          "Universal Stream Hub never bypasses DRM, subscriptions or regional rights: playback opens in the provider's official app.",
        ],
      },
    };
  }
  return null;
}

export interface HomeResult {
  hero: MediaSummary | null;
  rows: Array<{ id: string; title: string; items: MediaSummary[] }>;
}

export async function homeTmdb(env: Env, region: string): Promise<HomeResult> {
  const genres = await genreMap(env);
  const rows: HomeResult["rows"] = [];
  const definitions: Array<{ path: string; type: MediaType; title: string; id: string; params?: Record<string, string | number> }> = [
    { id: "trending", title: "Trending this week", path: "/trending/movie/week", type: "movie" },
    { id: "popular", title: "Popular movies", path: "/movie/popular", type: "movie" },
    { id: "new", title: "Recently released", path: "/movie/now_playing", type: "movie" },
    { id: "top", title: "Critically acclaimed", path: "/movie/top_rated", type: "movie" },
    { id: "upcoming", title: "Upcoming", path: "/movie/upcoming", type: "movie" },
    { id: "tv", title: "Popular TV shows", path: "/tv/popular", type: "tv" },
  ];

  for (const definition of definitions) {
    const payload = await tmdbGet(env, definition.path, { page: 1, region, watch_region: region });
    const items = asArray(payload?.results).slice(0, 18).map((entry) => mapMedia(asRaw(entry), definition.type, genres));
    if (items.length > 0) rows.push({ id: definition.id, title: definition.title, items });
  }

  const allItems = rows.flatMap((row) => row.items);
  const hero = allItems.find((item) => item.backdropUrl) ?? allItems[0] ?? null;
  return { hero, rows };
}

export function discoverProviderIds(): Record<string, number> {
  return PROVIDER_IDS;
}
