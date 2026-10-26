import type { MetadataRoute } from "next"
import { cityHasNotices } from "@/api/cities"
import { fetchItems } from "@/api/items"
import { GUIDE_SLUGS } from "@/content/guides"
import { paths } from "@/i18n/paths"
import { routing } from "@/i18n/routing"
import { CACHE } from "@/lib/cache"
import { CITIES } from "@/lib/cities"
import { toIsoDate } from "@/lib/intl/dates"
import { ITEM_KINDS, type ItemKind, isItemKind } from "@/lib/kinds"
import { absoluteUrl, isStale, itemLocale, sitemapIds } from "@/lib/seo"

export const revalidate = 3600

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

function pageEntry(path: string): MetadataRoute.Sitemap[number] {
  return {
    url: absoluteUrl(routing.defaultLocale, path),
    changeFrequency: path === "" ? "daily" : "hourly",
    priority: path === "" ? 1 : 0.8,
    alternates: alternates(path),
  }
}

async function pageEntries(): Promise<MetadataRoute.Sitemap> {
  const pages: MetadataRoute.Sitemap = [
    "",
    paths.list("lost"),
    paths.list("found"),
    paths.report("lost"),
    paths.report("found"),
    paths.safety,
  ].map(pageEntry)

  const guides: MetadataRoute.Sitemap = [
    paths.guides,
    ...GUIDE_SLUGS.map((slug) => paths.guide(slug)),
  ].map((path) => ({
    url: absoluteUrl(routing.defaultLocale, path),
    changeFrequency: "monthly",
    priority: 0.7,
    alternates: alternates(path),
  }))

  const documents: MetadataRoute.Sitemap = [paths.terms, paths.privacy].map((path) => ({
    url: absoluteUrl(routing.defaultLocale, path),
    changeFrequency: "monthly",
    priority: 0.2,
    alternates: alternates(path),
  }))

  const cities = await Promise.all(
    CITIES.map(async (city) => {
      const withNotices = (
        await Promise.all(
          ITEM_KINDS.map(async (kind) => ((await cityHasNotices(city, kind)) ? kind : null)),
        )
      ).filter((kind) => kind !== null)
      if (withNotices.length === 0) return []
      return [
        paths.city(city.slug),
        ...withNotices.map((kind) => paths.cityList(city.slug, kind)),
      ].map(pageEntry)
    }),
  )

  return [...pages, ...guides, ...documents, ...cities.flat()]
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
      url: absoluteUrl(itemLocale(item.language), paths.item(kind, item.id)),
      // The notice's own date: there is no "updated at" in the API yet (see README).
      lastModified: item.date,
      priority: 0.6,
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
  if (!isItemKind(kind) || !/^\d+$/.test(back ?? "")) {
    return []
  }

  const entries = await itemEntries(kind, Number(back))
  return entries.length > 0 ? entries : [pageEntry(paths.list(kind))]
}
