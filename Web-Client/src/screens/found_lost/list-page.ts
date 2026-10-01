import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { cache } from "react"
import {
  type Category,
  fetchCategories,
  fetchItems,
  type ItemKind,
  type ItemPage,
} from "@/api/items"
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

const load = cache(async (kind: ItemKind, rawJson: string): Promise<ListPage> => {
  const raw = JSON.parse(rawJson) as RawSearchParams
  const search = parseItemSearch(raw)
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

export function loadListPage(kind: ItemKind, raw: RawSearchParams): Promise<ListPage> {
  return load(kind, JSON.stringify(raw))
}

export async function listMetadata(
  kind: ItemKind,
  locale: string,
  raw: RawSearchParams,
): Promise<Metadata> {
  const [t, { after, nextQuery }] = await Promise.all([
    getTranslations({ locale, namespace: "list" }),
    loadListPage(kind, raw),
  ])

  const path = after ? `/${kind}?after=${encodeURIComponent(after)}` : `/${kind}`

  return {
    title: kind === "lost" ? t("lostTitle") : t("foundTitle"),
    description: kind === "lost" ? t("lostDescription") : t("foundDescription"),
    alternates: pageAlternates(locale, path),
    pagination: nextQuery ? { next: `/${locale}/${kind}?${nextQuery}` } : undefined,
  }
}
