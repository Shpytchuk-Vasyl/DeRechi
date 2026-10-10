import path from "node:path"
import { loadEnvConfig } from "@next/env"

export const WEB_CLIENT_DIR = path.resolve(__dirname, "../..")

function loadDotEnvLocal(): void {
  const vars = process.env as Record<string, string | undefined>
  const nodeEnv = vars.NODE_ENV
  if (nodeEnv === "test") delete vars.NODE_ENV
  const silent = { info: () => {}, error: (...args: unknown[]) => console.error(...args) }
  loadEnvConfig(WEB_CLIENT_DIR, false, silent)
  if (nodeEnv === undefined) delete vars.NODE_ENV
  else vars.NODE_ENV = nodeEnv
  delete vars.__NEXT_PROCESSED_ENV
}

loadDotEnvLocal()

const TRUE = new Set(["true", "1", "yes", "on", "y", "enabled"])
const FALSE = new Set(["false", "0", "no", "off", "n", "disabled"])

function flag(name: string, value: string | undefined, fallback: boolean): boolean {
  if (value === undefined) return fallback
  const normalized = value.trim().toLowerCase()
  if (TRUE.has(normalized)) return true
  if (FALSE.has(normalized)) return false
  throw new Error(`Invalid environment: ${name} must be a boolean`)
}

function pick(...values: (string | undefined)[]): string | undefined {
  return values.find((value) => value !== undefined && value !== "")
}

function required(name: string, value: string | undefined): string {
  if (!value) throw new Error(`e2e: ${name} is not set (in .env.local or as E2E_*)`)
  return value
}

const e = process.env

export type Flags = {
  smsOutage: boolean
}

export const flags: Flags = {
  smsOutage: flag("NEXT_PUBLIC_SMS_OUTAGE", e.NEXT_PUBLIC_SMS_OUTAGE, true),
}

export const env = {
  baseURL: pick(e.E2E_BASE_URL) ?? "http://localhost:3000",
  graphqlURL: required("GRAPHQL_URL", pick(e.E2E_GRAPHQL_URL, e.GRAPHQL_URL)),
  sharedSuffix: pick(e.E2E_SHARED_SUFFIX) ?? "",
  siteURL: pick(e.NEXT_PUBLIC_SITE_URL) ?? "http://localhost:3000",
  filesURL: pick(e.NEXT_PUBLIC_FILES_URL),
  maxUploadBytes: Number(pick(e.S3_MAX_UPLOAD_BYTES) ?? 5 * 1024 * 1024),
  s3: {
    publicEndpoint: pick(e.S3_PUBLIC_ENDPOINT),
    bucket: pick(e.S3_BUCKET) ?? "derechi-files",
    region: pick(e.S3_REGION) ?? "us-east-1",
    expiresIn: Number(pick(e.S3_UPLOAD_EXPIRES_SECONDS) ?? 300),
  },
}

export const secrets = {
  get s3AccessKey(): string {
    return required("S3_ACCESS_KEY", pick(e.S3_ACCESS_KEY))
  },
  get s3SecretKey(): string {
    return required("S3_SECRET_KEY", pick(e.S3_SECRET_KEY))
  },
}

export const allowedHosts: string[] = [
  env.baseURL,
  env.siteURL,
  env.graphqlURL,
  env.filesURL,
  env.s3.publicEndpoint,
]
  .filter((url): url is string => Boolean(url))
  .map((url) => new URL(url).host)

export const blockedHosts = ["maps.googleapis.com", "maps.gstatic.com"]

export type AllowedConsoleError = {
  pattern: RegExp
  reason: string
}

export const allowedConsoleErrors: AllowedConsoleError[] = [
  {
    pattern: /maps\.(googleapis|gstatic)\.com/,
    reason: "The fixtures abort Google Maps on purpose, so the browser reports the failed script",
  },
  {
    pattern: /The Google Maps JavaScript API failed to load/,
    reason:
      "@vis.gl/react-google-maps logs the aborted load; PlacePicker falls back to KnownPlaceSearch",
  },
  {
    pattern: /Failed to load resource: .* @ \S*\/_next\/image\?/,
    reason:
      "Somebody else's notice in the shared database may point to a missing photo; ItemPhoto falls back to category art",
  },
]
