import type { Metadata } from "next"
import type { ItemKind } from "@/api/items"
import { defaultLocale, locales } from "@/i18n/routing"
import { clientEnv } from "@/lib/env/client"

export const SITE_URL = clientEnv.NEXT_PUBLIC_SITE_URL.replace(/\/$/, "")

const LOCAL_HOST =
  /^(localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+|.*\.local)$/i

export const INDEXABLE = false; //temporary offline;
  //process.env.NODE_ENV === "production" && !LOCAL_HOST.test(new URL(SITE_URL).hostname)

export const STALE_AFTER_DAYS = 30

const DESCRIPTION_LIMIT = 160

export function isStale(date: string, now = new Date()): boolean {
  const age = now.getTime() - new Date(date).getTime()
  return age > STALE_AFTER_DAYS * 24 * 60 * 60 * 1000
}

export function pageAlternates(locale: string, path: string): NonNullable<Metadata["alternates"]> {
  return {
    canonical: `/${locale}${path}`,
    languages: {
      ...Object.fromEntries(locales.map((it) => [it, `/${it}${path}`])),
      "x-default": `/${defaultLocale}${path}`,
    },
  }
}

export function absoluteUrl(locale: string, path = ""): string {
  return `${SITE_URL}/${locale}${path}`
}

export function snippet(text: string, limit = DESCRIPTION_LIMIT): string {
  const flat = text.replace(/\s+/g, " ").trim()
  if (flat.length <= limit) return flat

  const cut = flat.slice(0, limit - 1)
  const lastSpace = cut.lastIndexOf(" ")
  return `${lastSpace > limit / 2 ? cut.slice(0, lastSpace) : cut}…`
}

export function jsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/[<>&\u2028\u2029]/g, (char) => JSON_LD_ESCAPES[char] ?? char)
}

const JSON_LD_ESCAPES: Record<string, string> = {
  "<": "\\u003c",
  ">": "\\u003e",
  "&": "\\u0026",
  "\u2028": "\\u2028",
  "\u2029": "\\u2029",
}

export const SITEMAP_KINDS: ItemKind[] = ["lost", "found"]

const SITEMAP_MONTHS_BACK = Math.ceil(STALE_AFTER_DAYS / 30) + 1

export function sitemapIds(): { id: string }[] {
  const months = Array.from({ length: SITEMAP_MONTHS_BACK }, (_, index) => index)
  return [
    { id: "pages" },
    ...SITEMAP_KINDS.flatMap((kind) => months.map((back) => ({ id: `${kind}-${back}` }))),
  ]
}
