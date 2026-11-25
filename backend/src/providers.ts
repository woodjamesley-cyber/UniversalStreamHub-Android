export interface ProviderDefinition {
  name: string;
  packageName: string;
  webUrl: string;
  offlineInOwnApp: boolean;
  tmdbIds: number[];
}

/** Official Android packages and sites used for authorised handoff. */
export const PROVIDERS: ProviderDefinition[] = [
  { name: "Netflix", packageName: "com.netflix.mediaclient", webUrl: "https://www.netflix.com/search", offlineInOwnApp: true, tmdbIds: [8, 1796] },
  { name: "Disney+", packageName: "com.disney.disneyplus", webUrl: "https://www.disneyplus.com", offlineInOwnApp: true, tmdbIds: [337, 390] },
  { name: "Prime Video", packageName: "com.amazon.avod.thirdpartyclient", webUrl: "https://www.primevideo.com", offlineInOwnApp: true, tmdbIds: [9, 10, 119, 2100] },
  { name: "Hulu", packageName: "com.hulu.plus", webUrl: "https://www.hulu.com", offlineInOwnApp: true, tmdbIds: [15, 1899] },
  { name: "Max", packageName: "com.wbd.stream", webUrl: "https://www.max.com", offlineInOwnApp: true, tmdbIds: [1899, 384] },
  { name: "Paramount+", packageName: "com.cbs.app", webUrl: "https://www.paramountplus.com", offlineInOwnApp: true, tmdbIds: [531, 2303, 1853] },
  { name: "Peacock", packageName: "com.peacocktv.peacockandroid", webUrl: "https://www.peacocktv.com", offlineInOwnApp: true, tmdbIds: [386, 1963, 1964] },
  { name: "Apple TV+", packageName: "com.apple.atve.androidtv.appletv", webUrl: "https://tv.apple.com", offlineInOwnApp: true, tmdbIds: [350, 2] },
  { name: "Crunchyroll", packageName: "com.crunchyroll.crunchyroid", webUrl: "https://www.crunchyroll.com", offlineInOwnApp: true, tmdbIds: [283, 1914] },
  { name: "Tubi", packageName: "com.tubitv", webUrl: "https://tubitv.com", offlineInOwnApp: true, tmdbIds: [73] },
  { name: "Pluto TV", packageName: "tv.pluto.android", webUrl: "https://pluto.tv", offlineInOwnApp: false, tmdbIds: [300] },
  { name: "YouTube Movies", packageName: "com.google.android.youtube", webUrl: "https://www.youtube.com/feed/storefront", offlineInOwnApp: true, tmdbIds: [192, 188] },
  { name: "The Roku Channel", packageName: "com.roku.rokubroadcast", webUrl: "https://therokuchannel.roku.com", offlineInOwnApp: false, tmdbIds: [328] },
  { name: "Plex", packageName: "com.plexapp.android", webUrl: "https://app.plex.tv", offlineInOwnApp: true, tmdbIds: [301, 538] },
  { name: "Kanopy", packageName: "com.kanopy.app", webUrl: "https://www.kanopy.com", offlineInOwnApp: false, tmdbIds: [1915] },
];

export interface ProviderLink {
  providerId: number | null;
  name: string;
  packageName: string;
  webUrl: string;
  offlineInOwnApp: boolean;
  logoUrl: string | null;
  offerType: string | null;
}

export function linkForProvider(name: string, providerId: number | null = null, logoPath: string | null = null): ProviderLink | null {
  const definition = PROVIDERS.find(
    (provider) => provider.name.toLowerCase() === name.toLowerCase() || provider.tmdbIds.includes(providerId ?? -1),
  );
  if (!definition) return null;
  return {
    providerId,
    name: definition.name,
    packageName: definition.packageName,
    webUrl: definition.webUrl,
    offlineInOwnApp: definition.offlineInOwnApp,
    logoUrl: logoPath ? `https://image.tmdb.org/t/p/w92${logoPath}` : null,
    offerType: "subscription",
  };
}

export function providerLinksForNames(names: string[]): ProviderLink[] {
  const seen = new Set<string>();
  const links: ProviderLink[] = [];
  for (const name of names) {
    const link = linkForProvider(name);
    if (link && !seen.has(link.name)) {
      seen.add(link.name);
      links.push(link);
    }
  }
  return links;
}

export function providerCatalog(): Array<Omit<ProviderLink, "providerId" | "logoUrl" | "offerType">> {
  return PROVIDERS.map((provider) => ({
    name: provider.name,
    packageName: provider.packageName,
    webUrl: provider.webUrl,
    offlineInOwnApp: provider.offlineInOwnApp,
  }));
}
