import { updateTag } from "next/cache"
import type { ItemKind } from "@/api/items"

export const CACHE_TTL = {
  minute: 60,
  fiveMinutes: 300,
  hour: 3600,
  day: 86_400,
} as const

export const CACHE_TAG = {
  items: (kind: ItemKind) => `items:${kind}`,
  item: (kind: ItemKind, id: string) => `item:${kind}:${id}`,
  places: "places",
  categories: "categories",
  countries: "countries",
  stats: "stats",
} as const

export type CachePolicy = { revalidate: number; tags: string[] }

export const CACHE = {
  items: (kind: ItemKind): CachePolicy => ({
    revalidate: CACHE_TTL.minute,
    tags: [CACHE_TAG.items(kind)],
  }),
  sitemapItems: (kind: ItemKind): CachePolicy => ({
    revalidate: CACHE_TTL.day,
    tags: [CACHE_TAG.items(kind)],
  }),
  item: (kind: ItemKind, id: string): CachePolicy => ({
    revalidate: CACHE_TTL.hour,
    tags: [CACHE_TAG.item(kind, id)],
  }),
  places: (): CachePolicy => ({ revalidate: CACHE_TTL.minute, tags: [CACHE_TAG.places] }),
  categories: (): CachePolicy => ({ revalidate: CACHE_TTL.day, tags: [CACHE_TAG.categories] }),
  countries: (): CachePolicy => ({ revalidate: CACHE_TTL.day, tags: [CACHE_TAG.countries] }),
  stats: (): CachePolicy => ({ revalidate: CACHE_TTL.day, tags: [CACHE_TAG.stats] }),
}

export const NO_STORE = { cache: "no-store" } as const

export function invalidate(...tags: string[]): void {
  for (const tag of tags) {
    updateTag(tag)
  }
}
