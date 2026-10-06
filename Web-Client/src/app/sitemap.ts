import type { MetadataRoute } from "next"
import { fetchItems, type ItemKind } from "@/api/items"
import { paths } from "@/i18n/paths"
import { routing } from "@/i18n/routing"
import { CACHE } from "@/lib/cache"
import { toIsoDate } from "@/lib/intl/dates"
import { absoluteUrl, isStale, SITEMAP_KINDS as KINDS, sitemapIds } from "@/lib/seo"

export const revalidate = 86400

const PAGE_SIZE = 100

const MAX_URLS = 50_000

export function generateSitemaps() {
  return sitemapIds()
}

function alternates(path: string) {
  return {
    languages: {
      ...Object.fromEntries(routing.locales.map((locale) => [locale, absoluteUrl(locale, path)])),
      "x-default": absoluteUrl(routing.defaultLocale, path),
    },
  }
}

function pageEntries(): MetadataRoute.Sitemap {
  const pages: MetadataRoute.Sitemap = [
    "",
    paths.list("lost"),
    paths.list("found"),
    paths.report("lost"),
    paths.report("found"),
    paths.safety,
  ].map((path) => ({
    url: absoluteUrl(routing.defaultLocale, path),
    changeFrequency: path === "" ? "daily" : "hourly",
    priority: path === "" ? 1 : 0.8,
    alternates: alternates(path),
  }))

  const documents: MetadataRoute.Sitemap = [paths.terms, paths.privacy].map((path) => ({
    url: absoluteUrl(routing.defaultLocale, path),
    changeFrequency: "yearly",
    priority: 0.2,
    alternates: alternates(path),
  }))

  return [...pages, ...documents]
}

function monthWindow(back: number, now = new Date()): { dateFrom: string; dateTo: string } {
  const first = new Date(now.getFullYear(), now.getMonth() - back, 1)
  const last = new Date(now.getFullYear(), now.getMonth() - back + 1, 0)
  return { dateFrom: toIsoDate(first), dateTo: toIsoDate(last) }
}

async function itemEntries(kind: ItemKind, back: number): Promise<MetadataRoute.Sitemap> {
  const filter = monthWindow(back)
  const items = []
  let after: string | null | undefined

  while (items.length < MAX_URLS) {
    const page = await fetchItems(
      kind,
      { filter, first: PAGE_SIZE, after },
      CACHE.sitemapItems(kind),
    )
    items.push(...page.items)

    if (!page.hasNextPage || !page.endCursor) {
      break
    }
    after = page.endCursor
  }

  return items
    .filter((item) => !isStale(item.date))
    .slice(0, MAX_URLS)
    .map((item) => ({
      url: absoluteUrl(routing.defaultLocale, paths.item(kind, item.id)),
      // The notice's own date: there is no "updated at" in the API yet (see README).
      lastModified: item.date,
      priority: 0.6,
      alternates: alternates(paths.item(kind, item.id)),
    }))
}

export default async function sitemap({
  id,
}: {
  id: Promise<string>
}): Promise<MetadataRoute.Sitemap> {
  const chunk = await id
  if (chunk === "pages") {
    return pageEntries()
  }

  const [kind, back] = chunk.split("-")
  if (!KINDS.includes(kind as ItemKind) || !/^\d+$/.test(back ?? "")) {
    return []
  }

  return itemEntries(kind as ItemKind, Number(back))
}
