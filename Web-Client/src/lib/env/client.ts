// No zod here: this module reaches every client bundle through fileUrl, and zod would ride along.

const TRUE = new Set(["true", "1", "yes", "on", "y", "enabled"])
const FALSE = new Set(["false", "0", "no", "off", "n", "disabled"])

function url(name: string, value: string | undefined): string {
  if (!value || !URL.canParse(value)) {
    throw new Error(`Invalid public environment: ${name} must be a URL`)
  }
  return value
}

function flag(name: string, value: string | undefined, fallback: boolean): boolean {
  if (value === undefined) return fallback
  const normalized = value.trim().toLowerCase()
  if (TRUE.has(normalized)) return true
  if (FALSE.has(normalized)) return false
  throw new Error(`Invalid public environment: ${name} must be a boolean`)
}

export const clientEnv = {
  NEXT_PUBLIC_SITE_URL: url("NEXT_PUBLIC_SITE_URL", process.env.NEXT_PUBLIC_SITE_URL),
  NEXT_PUBLIC_MAPS_API_KEY: process.env.NEXT_PUBLIC_MAPS_API_KEY,
  NEXT_PUBLIC_FILES_URL: url("NEXT_PUBLIC_FILES_URL", process.env.NEXT_PUBLIC_FILES_URL),
  NEXT_PUBLIC_SMS_OUTAGE: flag("NEXT_PUBLIC_SMS_OUTAGE", process.env.NEXT_PUBLIC_SMS_OUTAGE, true),
  NEXT_PUBLIC_MAINTENANCE: flag(
    "NEXT_PUBLIC_MAINTENANCE",
    process.env.NEXT_PUBLIC_MAINTENANCE,
    false,
  ),
}

export function fileUrl(key: string): string {
  if (key.startsWith("items"))
    return `${clientEnv.NEXT_PUBLIC_FILES_URL}/${key.replace(/^\/+/, "")}`
  return `/files/${key.replace(/^\/+/, "")}`
}

export function absoluteFileUrl(key: string): string {
  return new URL(fileUrl(key), clientEnv.NEXT_PUBLIC_SITE_URL).toString()
}
