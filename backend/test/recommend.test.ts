import { describe, expect, it } from "vitest";
import { buildRecommendations, genreWeights, scoreItem } from "../src/recommend";
import type { MediaSummary } from "../src/types";

function media(id: string, genres: string[], rating = 7): MediaSummary {
  return {
    id,
    source: "tmdb",
    type: "movie",
    title: id,
    overview: "",
    posterUrl: null,
    backdropUrl: null,
    releaseDate: null,
    year: null,
    voteAverage: rating,
    genres,
    runtimeMinutes: null,
    providers: [],
    playback: "PROVIDER_APP",
    downloadable: false,
    license: null,
    quality: null,
    fileSizeBytes: null,
    trailerYoutubeKey: null,
  };
}

describe("recommendation engine", () => {
  const candidates = [
    media("thriller-a", ["Thriller"], 8),
    media("thriller-b", ["Thriller", "Crime"], 6),
    media("comedy", ["Comedy"], 9),
  ];

  it("weights favourite genres above incidental history", () => {
    const weights = genreWeights({
      favoriteGenres: ["Thriller"],
      watchlist: [{ id: "w1", genres: ["Comedy"] }],
      history: [{ id: "h1", genres: ["Drama"] }],
    });
    expect(weights.get("thriller")).toBe(3);
    expect(weights.get("comedy")).toBe(2);
    expect(weights.get("drama")).toBe(1);
    expect(scoreItem(candidates[0] as MediaSummary, weights)).toBeGreaterThan(0);
  });

  it("never recommends titles the user already saved or watched", () => {
    const result = buildRecommendations(candidates, {
      favoriteGenres: ["Thriller"],
      watchlist: [{ id: "thriller-a", genres: ["Thriller"] }],
      history: [{ id: "comedy", genres: ["Comedy"] }],
    });
    const ids = result.rows.flatMap((row) => row.items.map((item) => item.id));
    expect(ids).toContain("thriller-b");
    expect(ids).not.toContain("thriller-a");
    expect(ids).not.toContain("comedy");
    expect(result.personalized).toBe(true);
  });

  it("explains itself when there are no signals yet", () => {
    const result = buildRecommendations(candidates, { favoriteGenres: [], watchlist: [], history: [] });
    expect(result.personalized).toBe(false);
    expect(result.rows.length).toBeGreaterThan(0);
    expect(result.reason).toContain("My List");
  });
});
