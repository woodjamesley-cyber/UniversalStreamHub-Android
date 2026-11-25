import { describe, expect, it } from "vitest";
import { identifierFromId, openEntryId, toOpenSummary } from "../src/catalog";
import { linkForProvider, providerCatalog, providerLinksForNames } from "../src/providers";

describe("free public-domain catalog", () => {
  it("uses a stable open: id namespace", () => {
    expect(openEntryId("night_of_the_living_dead")).toBe("open:night_of_the_living_dead");
    expect(identifierFromId("open:night_of_the_living_dead")).toBe("night_of_the_living_dead");
    expect(identifierFromId("tmdb:155")).toBeNull();
  });

  it("marks free titles as natively playable and downloadable", () => {
    const summary = toOpenSummary({
      identifier: "night_of_the_living_dead",
      title: "Night of the Living Dead",
      year: 1968,
      overview: "A public-domain horror classic.",
      genres: ["Horror"],
      downloads: 100,
    });
    expect(summary.playback).toBe("NATIVE");
    expect(summary.downloadable).toBe(true);
    expect(summary.license).toContain("Public domain");
    expect(summary.posterUrl).toContain("archive.org/services/img/");
  });
});

describe("provider handoff catalog", () => {
  it("resolves known providers by name or TMDB id", () => {
    expect(linkForProvider("Netflix")?.packageName).toBe("com.netflix.mediaclient");
    expect(linkForProvider("Disney+")?.packageName).toBe("com.disney.disneyplus");
    expect(linkForProvider("Unknown Service")).toBeNull();
    expect(providerLinksForNames(["Netflix", "Netflix", "Max"])).toHaveLength(2);
  });

  it("exposes the full provider catalog for discovery filters", () => {
    const catalog = providerCatalog();
    expect(catalog.length).toBeGreaterThanOrEqual(10);
    expect(catalog.every((provider) => provider.packageName.length > 0)).toBe(true);
  });
});
