import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { cache } from "react"
import { cityHasNotices } from "@/api/cities"
import {
  type Category,
  fetchCategories,
  fetchItems,
  type ItemKind,
  type ItemPage,
} from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
import { paths } from "@/i18n/paths"
import { cityBySlug } from "@/lib/cities"
import { type ItemSearch, parseItemSearch, type RawSearchParams, toFilter } from "@/lib/item-search"
import { pageAlternates } from "@/lib/seo"

export type ListPage = {
  search: ItemSearch
  categories: Category[]
  page: ItemPage
  after?: string
  nextQuery?: string
}

function single(value: string | string[] | undefined): string | undefined {
  const first = Array.isArray(value) ? value[0] : value
  return first?.trim() || undefined
}

function nextQuery(raw: RawSearchParams, after: string): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(raw)) {
    const first = single(value)
    if (first) query.set(key, first)
  }
  query.set("after", after)
  return query.toString()
}

const load = cache(async (kind: ItemKind, rawJson: string, city?: string): Promise<ListPage> => {
  const raw = JSON.parse(rawJson) as RawSearchParams
  const search = parseItemSearch(raw, city)
  const after = single(raw.after)
  const categories = await fetchCategories()
  const page = await fetchItems(kind, {
    filter: toFilter(search, categories),
    sort: search.sort,
    after,
  })

  return {
    search,
    categories,
    page,
    after,
    nextQuery: page.hasNextPage && page.endCursor ? nextQuery(raw, page.endCursor) : undefined,
  }
})

export function loadListPage(
  kind: ItemKind,
  raw: RawSearchParams,
  city?: string,
): Promise<ListPage> {
  return load(kind, JSON.stringify(raw), city)
}

export async function listMetadata(
  kind: ItemKind,
  locale: string,
  raw: RawSearchParams,
  slug?: string,
): Promise<Metadata> {
  const city = slug ? cityBySlug(slug) : undefined
  if (slug && !city) return {}

  const [t, tc, { after, nextQuery }, hasNotices] = await Promise.all([
    getTranslations({ locale, namespace: "list" }),
    getTranslations({ locale, namespace: "city" }),
    loadListPage(kind, raw, city?.slug),
    city ? cityHasNotices(city, { kind }) : true,
  ])

  const base = city ? paths.cityList(city.slug, kind) : paths.list(kind)
  const path = after ? `${base}?after=${encodeURIComponent(after)}` : base
  const { list } = KIND_META[kind]
  const where = city ? tc(`${city.slug}.in`) : undefined

  return {
    title: where ? t(list.titleIn, { in: where }) : t(list.title),
    description: where ? t(list.metaDescriptionIn, { in: where }) : t(list.metaDescription),
    alternates: pageAlternates(locale, path),
    pagination: nextQuery ? { next: `/${locale}${base}?${nextQuery}` } : undefined,
    ...(hasNotices ? {} : { robots: { index: false, follow: true } }),
  }
}
