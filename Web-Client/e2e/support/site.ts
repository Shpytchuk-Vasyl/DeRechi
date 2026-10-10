import type { BrowserContext } from "@playwright/test"
import type { ConsoleErrors } from "../fixtures/base"
import type { Locale } from "./data"
import { env } from "./env"

export const LOCALE_COOKIE = "DERECHI_LOCALE"

export const COUNTRY_COOKIE = "DERECHI_COUNTRY"
export const GEO_COUNTRY_HEADER = "x-vercel-ip-country"

export const DEFAULT_LOCALE: Locale = "en"

export function otherLocale(locale: Locale): Locale {
  return locale === "en" ? "uk" : "en"
}

export function siteUrl(locale: Locale, path = ""): string {
  return `${env.siteURL.replace(/\/$/, "")}/${locale}${path}`
}

export function pathnameOf(location: string | undefined): string {
  if (!location) return ""
  return new URL(location, env.baseURL).pathname
}

export async function setCountry(context: BrowserContext, code: string): Promise<void> {
  await context.addCookies([{ name: COUNTRY_COOKIE, value: code, url: env.baseURL }])
}

export function allowDocumentStatus(consoleErrors: ConsoleErrors, ...statuses: number[]): void {
  const codes = statuses.join("|")
  consoleErrors.allow(
    new RegExp(`Failed to load resource: .*status of (${codes})\\b`),
    `the page answers ${codes} on purpose`,
  )
}
