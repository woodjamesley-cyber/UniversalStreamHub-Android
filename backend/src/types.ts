export type MediaType = "movie" | "tv";

export interface ProviderLink {
  providerId: number | null;
  name: string;
  packageName: string;
  webUrl: string;
  offlineInOwnApp: boolean;
  logoUrl: string | null;
  offerType: string | null;
}

export interface MediaSummary {
  id: string;
  source: string;
  type: MediaType;
  title: string;
  overview: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  releaseDate: string | null;
  year: number | null;
  voteAverage: number;
  genres: string[];
  runtimeMinutes: number | null;
  providers: ProviderLink[];
  playback: "NATIVE" | "PROVIDER_APP";
  downloadable: boolean;
  license: string | null;
  quality: string | null;
  fileSizeBytes: number | null;
  trailerYoutubeKey: string | null;
}

export interface HomeRow {
  id: string;
  title: string;
  items: MediaSummary[];
}

export interface NativeSource {
  id: string;
  label: string;
  url: string;
  mimeType: string;
  quality: string | null;
  downloadAllowed: boolean;
  fileSizeBytes: number | null;
  license: string;
}

export interface PlaybackOptions {
  native: NativeSource[];
  providerApps: ProviderLink[];
  notes: string[];
}

export interface CastMember {
  name: string;
  character: string | null;
  profileUrl: string | null;
}

export interface CrewMember {
  name: string;
  job: string;
}

export interface SeasonSummary {
  seasonNumber: number;
  name: string;
  episodeCount: number;
  airDate: string | null;
  posterUrl: string | null;
}

export interface TitleDetail {
  media: MediaSummary;
  cast: CastMember[];
  crew: CrewMember[];
  trailer: { name: string; youtubeKey: string; webUrl: string } | null;
  similar: MediaSummary[];
  seasons: SeasonSummary[];
  playback: PlaybackOptions;
}

export interface SearchResponse {
  page: number;
  totalPages: number;
  totalResults: number;
  items: MediaSummary[];
  people: Array<{ id: number; name: string; profileUrl: string | null; knownFor: string }>;
  tmdbConfigured: boolean;
  notice: string | null;
}

export interface RecommendationResult {
  rows: HomeRow[];
  genres: string[];
  reason: string | null;
  personalized: boolean;
}
