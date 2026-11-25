import type { Env } from "./env";
import type { MediaSummary, NativeSource } from "./types";

/**
 * The free catalog is resolved live from the Internet Archive instead of being
 * hard-coded, so every title offered for native playback actually exists and
 * carries an explicit public-domain / Creative Commons mark. Nothing here
 * touches subscription services: those are handed to their own apps.
 */

export interface OpenEntry {
  identifier: string;
  title: string;
  year: number | null;
  overview: string;
  genres: string[];
  downloads: number;
}

const PD_LICENSE = "http://creativecommons.org/publicdomain/mark/1.0/";
const LIST_TTL_MS = 6 * 60 * 60 * 1000;
const SOURCE_TTL_MS = 12 * 60 * 60 * 1000;

let listCache: { expires: number; items: OpenEntry[] } | null = null;
const sourceCache = new Map<string, { expires: number; sources: NativeSource[] }>();

function clean(value: unknown, max = 500): string {
  if (typeof value !== "string") return "";
  const text = value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function toYear(value: unknown): number | null {
  if (Array.isArray(value)) return toYear(value[0]);
  if (typeof value === "number" && Number.isFinite(value)) return Math.trunc(value);
  if (typeof value === "string") {
    const match = /(18|19|20)\d{2}/.exec(value);
    if (match) return Number.parseInt(match[0], 10);
  }
  return null;
}

function toGenres(value: unknown): string[] {
  const raw = Array.isArray(value) ? value : typeof value === "string" ? value.split(/[;,]/) : [];
  const genres: string[] = [];
  for (const entry of raw) {
    if (typeof entry !== "string") continue;
    const genre = entry.trim();
    if (!genre || genre.length > 24) continue;
    const normalised = genre.charAt(0).toUpperCase() + genre.slice(1).toLowerCase();
    if (!genres.includes(normalised)) genres.push(normalised);
    if (genres.length >= 4) break;
  }
  return genres;
}

async function fetchWithTimeout(url: string, ms: number): Promise<Response | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { accept: "application/json" } });
    return response.ok ? response : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

interface ArchiveSearchDoc {
  identifier?: unknown;
  title?: unknown;
  year?: unknown;
  date?: unknown;
  description?: unknown;
  subject?: unknown;
  downloads?: unknown;
}

interface ArchiveSearchResponse {
  response?: { docs?: ArchiveSearchDoc[]; numFound?: number };
}

export async function loadOpenCatalog(force = false): Promise<OpenEntry[]> {
  if (!force && listCache && listCache.expires > Date.now()) return listCache.items;

  const url = new URL("https://archive.org/advancedsearch.php");
  url.searchParams.set("q", `mediatype:movies AND licenseurl:"${PD_LICENSE}" AND format:(MPEG4)`);
  url.searchParams.set("fl[]", "identifier");
  url.searchParams.append("fl[]", "title");
  url.searchParams.append("fl[]", "year");
  url.searchParams.append("fl[]", "date");
  url.searchParams.append("fl[]", "description");
  url.searchParams.append("fl[]", "subject");
  url.searchParams.append("fl[]", "downloads");
  url.searchParams.set("sort[]", "downloads desc");
  url.searchParams.set("rows", "32");
  url.searchParams.set("page", "1");
  url.searchParams.set("output", "json");

  const response = await fetchWithTimeout(url.toString(), 8_000);
  if (!response) {
    if (listCache) return listCache.items;
    return [];
  }

  let docs: ArchiveSearchDoc[] = [];
  try {
    const payload = (await response.json()) as ArchiveSearchResponse;
    docs = payload.response?.docs ?? [];
  } catch {
    return listCache?.items ?? [];
  }

  const items: OpenEntry[] = [];
  for (const doc of docs) {
    const identifier = typeof doc.identifier === "string" ? doc.identifier : "";
    const title = clean(doc.title, 120);
    if (!identifier || !title) continue;
    const year = toYear(doc.year) ?? toYear(doc.date);
    items.push({
      identifier,
      title,
      year,
      overview: clean(doc.description, 600),
      genres: toGenres(doc.subject),
      downloads: typeof doc.downloads === "number" ? doc.downloads : 0,
    });
  }

  listCache = { expires: Date.now() + LIST_TTL_MS, items };
  return items;
}

export function openEntryId(identifier: string): string {
  return `open:${identifier}`;
}

export function identifierFromId(id: string): string | null {
  return id.startsWith("open:") ? id.slice(5) : null;
}

export function toOpenSummary(entry: OpenEntry): MediaSummary {
  return {
    id: openEntryId(entry.identifier),
    source: "open",
    type: "movie",
    title: entry.title,
    overview: entry.overview,
    posterUrl: `https://archive.org/services/img/${encodeURIComponent(entry.identifier)}`,
    backdropUrl: null,
    releaseDate: entry.year ? `${entry.year}-01-01` : null,
    year: entry.year,
    voteAverage: 0,
    genres: entry.genres.length > 0 ? entry.genres : ["Public domain"],
    runtimeMinutes: null,
    providers: [],
    playback: "NATIVE",
    downloadable: true,
    license: "Public domain / Creative Commons Mark 1.0",
    quality: null,
    fileSizeBytes: null,
    trailerYoutubeKey: null,
  };
}

export async function openSummaries(limit = 24): Promise<MediaSummary[]> {
  const entries = await loadOpenCatalog();
  return entries.slice(0, limit).map(toOpenSummary);
}

interface ArchiveFile {
  name?: unknown;
  format?: unknown;
  size?: unknown;
  length?: unknown;
}

interface ArchiveMetadata {
  files?: ArchiveFile[];
  metadata?: { title?: unknown; year?: unknown; licenseurl?: unknown };
}

/** Resolves a real, playable MP4 for an Internet Archive item. */
export async function resolveOpenSources(identifier: string): Promise<NativeSource[]> {
  const cached = sourceCache.get(identifier);
  if (cached && cached.expires > Date.now()) return cached.sources;

  const response = await fetchWithTimeout(`https://archive.org/metadata/${encodeURIComponent(identifier)}`, 8_000);
  if (!response) return cached?.sources ?? [];

  let metadata: ArchiveMetadata = {};
  try {
    metadata = (await response.json()) as ArchiveMetadata;
  } catch {
    return [];
  }

  const candidates: Array<{ name: string; size: number }> = [];
  for (const file of metadata.files ?? []) {
    const name = typeof file.name === "string" ? file.name : "";
    const format = typeof file.format === "string" ? file.format : "";
    if (!name || !/\.(mp4|m4v)$/i.test(name)) continue;
    if (!/MPEG4|h\.264|512Kb|MP4/i.test(format) && !/\.mp4$/i.test(name)) continue;
    const size = typeof file.size === "string" ? Number.parseInt(file.size, 10) : typeof file.size === "number" ? file.size : 0;
    candidates.push({ name, size: Number.isFinite(size) ? size : 0 });
  }

  candidates.sort((a, b) => a.size - b.size);
  const sources: NativeSource[] = candidates.slice(0, 3).map((candidate, index) => ({
    id: `${identifier}:${index}`,
    label: index === 0 ? "Standard MP4" : `Alternate MP4 ${index + 1}`,
    url: `https://archive.org/download/${encodeURIComponent(identifier)}/${candidate.name.split("/").map(encodeURIComponent).join("/")}`,
    mimeType: "video/mp4",
    quality: candidate.size > 0 && candidate.size < 400 * 1024 * 1024 ? "480p" : "SD",
    downloadAllowed: true,
    fileSizeBytes: candidate.size > 0 ? candidate.size : null,
    license: "Public domain / Creative Commons Mark 1.0",
  }));

  sourceCache.set(identifier, { expires: Date.now() + SOURCE_TTL_MS, sources });
  return sources;
}

export async function findOpenEntry(id: string): Promise<OpenEntry | null> {
  const identifier = identifierFromId(id);
  if (!identifier) return null;
  const entries = await loadOpenCatalog();
  return entries.find((entry) => entry.identifier === identifier) ?? null;
}

export function openNotice(env: Env): string {
  return env.TMDB_API_KEY
    ? "Free public-domain films stream natively from the Internet Archive; licensed titles open in the provider's own app."
    : "Running without a TMDB key: the catalog shows the free public-domain films that play natively. Set TMDB_API_KEY on the server to unlock the full multi-provider catalog.";
}
