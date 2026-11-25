import type { HomeRow, MediaSummary, RecommendationResult } from "./types";

export interface TasteSignal {
  favoriteGenres: string[];
  watchlist: Array<{ id: string; genres: string[] }>;
  history: Array<{ id: string; genres: string[] }>;
}

const STOP = new Set(["", "public domain", "film", "movies", "feature"]);

function normalise(genre: string): string {
  return genre.trim().toLowerCase();
}

/**
 * Builds genre weights from real user signals only: the titles they saved, the
 * titles they actually opened, and the genres they picked. No fabricated
 * popularity numbers are used.
 */
export function genreWeights(signal: TasteSignal): Map<string, number> {
  const weights = new Map<string, number>();
  const add = (genre: string, amount: number) => {
    const key = normalise(genre);
    if (STOP.has(key)) return;
    weights.set(key, (weights.get(key) ?? 0) + amount);
  };
  signal.favoriteGenres.forEach((genre) => add(genre, 3));
  signal.watchlist.forEach((item) => item.genres.forEach((genre) => add(genre, 2)));
  signal.history.forEach((item) => item.genres.forEach((genre) => add(genre, 1)));
  return weights;
}

export function scoreItem(item: MediaSummary, weights: Map<string, number>): number {
  let score = 0;
  for (const genre of item.genres) {
    score += weights.get(normalise(genre)) ?? 0;
  }
  if (score > 0) {
    score += Math.min(item.voteAverage, 10) * 0.4;
  }
  return score;
}

export function buildRecommendations(
  candidates: MediaSummary[],
  signal: TasteSignal,
  options: { limit?: number; label?: string } = {},
): RecommendationResult {
  const limit = options.limit ?? 12;
  const weights = genreWeights(signal);
  const seen = new Set([...signal.watchlist.map((item) => item.id), ...signal.history.map((item) => item.id)]);

  const ranked = candidates
    .filter((item) => !seen.has(item.id))
    .map((item) => ({ item, score: scoreItem(item, weights) }))
    .sort((a, b) => b.score - a.score || b.item.voteAverage - a.item.voteAverage);

  const personalised = [...ranked].filter((entry) => entry.score > 0).slice(0, limit).map((entry) => entry.item);
  const fresh = [...ranked].filter((entry) => entry.score <= 0).slice(0, limit).map((entry) => entry.item);

  const genres = favoriteGenresOf(signal, weights, 4);
  const rows: HomeRow[] = [];
  if (personalised.length > 0) {
    rows.push({
      id: "because-you-watched",
      title: options.label ?? (genres[0] ? `Because you like ${genres[0]}` : "Recommended for you"),
      items: personalised,
    });
  }
  if (fresh.length > 0) {
    rows.push({ id: "popular-now", title: "Popular in the hub", items: fresh });
  }

  return {
    rows,
    genres,
    reason:
      personalised.length > 0
        ? "Built from the titles in your list, your viewing history and the genres you picked."
        : "Add titles to My List or watch something so recommendations can follow your taste.",
    personalized: personalised.length > 0,
  };
}

export function favoriteGenresOf(signal: TasteSignal, weights: Map<string, number>, limit = 4): string[] {
  const display = new Map<string, string>();
  const remember = (genre: string) => {
    const key = normalise(genre);
    if (!display.has(key) && !STOP.has(key)) display.set(key, genre.trim());
  };
  signal.favoriteGenres.forEach(remember);
  signal.watchlist.forEach((item) => item.genres.forEach(remember));
  signal.history.forEach((item) => item.genres.forEach(remember));

  return [...weights.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([key]) => display.get(key) ?? key.charAt(0).toUpperCase() + key.slice(1));
}
