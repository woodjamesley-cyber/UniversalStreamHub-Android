export interface Env {
  DB: D1Database;
  TMDB_API_KEY?: string;
  JWT_SECRET?: string;
  RESEND_API_KEY?: string;
  RESEND_FROM?: string;
  REGION?: string;
  ENVIRONMENT?: string;
}

export const DEFAULT_REGION = "US";
export const FALLBACK_JWT_SECRET = "universal-stream-hub-development-secret";
export const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30;

export function jwtSecret(env: Env): string {
  return env.JWT_SECRET && env.JWT_SECRET.length >= 16 ? env.JWT_SECRET : FALLBACK_JWT_SECRET;
}

export function region(env: Env, requested?: string | null): string {
  const value = (requested || env.REGION || DEFAULT_REGION).toUpperCase();
  return /^[A-Z]{2}$/.test(value) ? value : DEFAULT_REGION;
}
