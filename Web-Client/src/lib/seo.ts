import type { Metadata } from "next"
import type { ItemKind } from "@/api/items"
import { defaultLocale, locales } from "@/i18n/routing"
import { clientEnv } from "@/lib/env/client"

export const SITE_URL = clientEnv.NEXT_PUBLIC_SITE_URL.replace(/\/$/, "")

export const INDEXABLE = !!process.env.VERCEL

export const STALE_AFTER_DAYS = 30

export const ITEM_LOCALE = "uk"

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

export function joinSentences(first: string, second: string): string {
  return `${/[.!?…]$/.test(first) ? first : `${first}.`} ${second}`
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

const TELEGRAM_CHANNEL_URL = "https://t.me/derechi_ua"

export type SiteInfo = { name: string; alternateNames: string[]; description: string }

export type CityInfo = { slug: string; name: string; lat: number; lon: number }

function searchAction(urlTemplate: string) {
  return {
    "@type": "SearchAction",
    target: { "@type": "EntryPoint", urlTemplate },
    "query-input": "required name=search_term_string",
  }
}

export function websiteJsonLd(locale: string, site: SiteInfo) {
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: site.name,
    alternateName: site.alternateNames,
    description: site.description,
    inLanguage: locale,
    url: absoluteUrl(locale),
    publisher: {
      "@type": "Organization",
      name: site.name,
      url: `${SITE_URL}/`,
      logo: `${SITE_URL}/apple-icon.png`,
      sameAs: [TELEGRAM_CHANNEL_URL],
    },
    potentialAction: searchAction(`${absoluteUrl(locale, "/found")}?search={search_term_string}`),
  }
}

export function cityPageJsonLd(
  locale: string,
  site: SiteInfo,
  page: { title: string; description: string; home: string },
  city: CityInfo,
) {
  const url = absoluteUrl(locale, `/${city.slug}`)
  return [
    websiteJsonLd(locale, site),
    {
      "@context": "https://schema.org",
      "@type": "CollectionPage",
      name: page.title,
      description: page.description,
      inLanguage: locale,
      url,
      isPartOf: { "@type": "WebSite", name: site.name, url: absoluteUrl(locale) },
      about: {
        "@type": "City",
        name: city.name,
        geo: { "@type": "GeoCoordinates", latitude: city.lat, longitude: city.lon },
      },
      potentialAction: searchAction(
        `${absoluteUrl(locale, "/found")}?lat=${city.lat}&lon=${city.lon}&search={search_term_string}`,
      ),
      breadcrumb: {
        "@type": "BreadcrumbList",
        itemListElement: [
          { "@type": "ListItem", position: 1, name: page.home, item: absoluteUrl(locale) },
          { "@type": "ListItem", position: 2, name: city.name, item: url },
        ],
      },
    },
  ]
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
