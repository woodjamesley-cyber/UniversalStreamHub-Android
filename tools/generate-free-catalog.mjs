// Generates app/src/main/assets/free_catalog.json from live Internet Archive
// metadata. Every entry is only included when a direct MP4 stream responds with
// HTTP 200 and an explicit public-domain / Creative Commons licence is present.
import { writeFile, mkdir } from "node:fs/promises";
import { dirname } from "node:path";

const OUT = process.argv[2] ?? "app/src/main/assets/free_catalog.json";
const TARGET = 16;

// Broad-audience catalogue: keep exploitation, nudist and graphic documentary
// titles out even though they are legally public domain.
const UNSUITABLE = /nudist|naked witch|sex madness|nazi concentration|bloody pit|voyeur|orgy|prostitut/i;

const search = new URLSearchParams();
search.set("q", "collection:feature_films AND mediatype:movies");
for (const field of ["identifier", "title", "description", "year", "date", "downloads", "licenseurl", "subject", "creator"]) {
  search.append("fl[]", field);
}
search.set("rows", "250");
search.set("page", "1");
search.set("output", "json");
search.append("sort[]", "downloads desc");

const plain = (value, max = 400) => {
  if (typeof value !== "string" && !Array.isArray(value)) return "";
  const text = (Array.isArray(value) ? value.join(" ") : value)
    .replace(/<[^>]*>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
};

const first = (value) => (Array.isArray(value) ? value[0] : value);

function yearOf(...values) {
  for (const value of values) {
    const match = /(18|19|20)\d{2}/.exec(String(value ?? ""));
    if (match) return Number.parseInt(match[0], 10);
  }
  return null;
}

function runtimeMinutes(value) {
  const text = plain(value, 40);
  if (!text) return null;
  const clock = /(\d{1,2}):(\d{2})(?::(\d{2}))?/.exec(text);
  if (clock) {
    const [, a, b, c] = clock;
    return c ? Number(a) * 60 + Number(b) : Number(a);
  }
  const minutes = /(\d{2,3})\s*min/i.exec(text);
  return minutes ? Number(minutes[1]) : null;
}

function genresOf(value) {
  const raw = Array.isArray(value) ? value : String(value ?? "").split(/[;,]/);
  const out = [];
  for (const tag of raw) {
    const cleaned = plain(tag, 24);
    if (!cleaned || cleaned.length < 3) continue;
    const normalised = cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
    if (!out.includes(normalised)) out.push(normalised);
    if (out.length === 3) break;
  }
  return out.length ? out : ["Public domain"];
}

async function getJson(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  return response.json();
}

const docs = (await getJson(`https://archive.org/advancedsearch.php?${search}`)).response.docs;
console.log(`search returned ${docs.length} candidates`);

const items = [];
for (const doc of docs) {
  if (items.length >= TARGET) break;
  const identifier = String(doc.identifier ?? "");
  const title = plain(doc.title, 120);
  if (!identifier || !title) continue;
  if (UNSUITABLE.test(title) || UNSUITABLE.test(plain(doc.description, 2000))) {
    console.log(`skip ${title}: not suitable for the general catalogue`);
    continue;
  }

  let metadata;
  try {
    metadata = await getJson(`https://archive.org/metadata/${encodeURIComponent(identifier)}`);
  } catch (error) {
    console.warn(`skip ${identifier}: metadata ${error.message}`);
    continue;
  }

  const meta = metadata.metadata ?? {};
  const licence = String(meta.licenseurl ?? doc.licenseurl ?? "");
  if (!/publicdomain|creativecommons/i.test(licence)) {
    continue;
  }

  const candidates = (metadata.files ?? [])
    .filter((file) => /\.mp4$/i.test(String(file.name ?? "")))
    .map((file) => ({
      name: String(file.name),
      size: Number(file.size ?? 0),
      format: String(file.format ?? ""),
    }))
    .filter((file) => file.size > 5_000_000 && file.size < 1_400_000_000)
    .sort((a, b) => b.size - a.size);
  if (!candidates.length) continue;

  const file = candidates[0];
  const url = `https://archive.org/download/${encodeURIComponent(identifier)}/${file.name.split("/").map(encodeURIComponent).join("/")}`;

  let reachable = false;
  try {
    const head = await fetch(url, { method: "HEAD", redirect: "follow", signal: AbortSignal.timeout(20_000) });
    const type = head.headers.get("content-type") ?? "";
    reachable = head.ok && (type.includes("video") || type.includes("octet-stream"));
    if (!reachable) console.warn(`skip ${identifier}: HEAD ${head.status} ${type}`);
  } catch (error) {
    console.warn(`skip ${identifier}: HEAD ${error.message}`);
    continue;
  }
  if (!reachable) continue;

  const year = yearOf(meta.year, meta.date, doc.year, doc.date);
  const description = plain(meta.description ?? doc.description, 420);
  const runtime = runtimeMinutes(meta.runtime);

  items.push({
    media: {
      id: `open:${identifier}`,
      source: "open",
      type: "movie",
      title,
      overview: description || "A public-domain feature film hosted by the Internet Archive.",
      posterUrl: `https://archive.org/services/img/${encodeURIComponent(identifier)}`,
      backdropUrl: null,
      releaseDate: year ? `${year}-01-01` : null,
      year,
      voteAverage: 0,
      genres: genresOf(meta.subject ?? doc.subject),
      runtimeMinutes: runtime,
      providers: [],
      playback: "NATIVE",
      downloadable: true,
      license: licence,
      quality: file.format || "MP4",
      fileSizeBytes: file.size,
      trailerYoutubeKey: null,
    },
    source: {
      id: `open:${identifier}`,
      label: `Internet Archive - ${file.format || "MP4"}`,
      url,
      mimeType: "video/mp4",
      quality: file.format || null,
      downloadAllowed: true,
      fileSizeBytes: file.size,
      license: licence,
    },
    director: plain(first(meta.creator ?? doc.creator), 80) || null,
  });
  console.log(`+ ${title} (${year ?? "n/a"}) ${(file.size / 1024 / 1024).toFixed(0)}MB`);
}

if (items.length < 8) {
  console.error(`only ${items.length} verified titles - refusing to write a thin catalog`);
  process.exit(1);
}

await mkdir(dirname(OUT), { recursive: true });
await writeFile(
  OUT,
  JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      source: "Internet Archive (archive.org)",
      note: "Each title was verified to expose a direct MP4 stream and an explicit public-domain or Creative Commons licence at generation time.",
      items,
    },
    null,
    2,
  ),
);
console.log(`wrote ${items.length} titles to ${OUT}`);
