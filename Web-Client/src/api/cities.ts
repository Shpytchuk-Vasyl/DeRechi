import "server-only"
import { fetchItems } from "@/api/items"
import { CACHE, type CachePolicy } from "@/lib/cache"
import { type City, cityArea } from "@/lib/cities"
import { ITEM_KINDS, type ItemKind } from "@/lib/kinds"

type Options = {
  kind?: ItemKind
  cache?: (kind: ItemKind) => CachePolicy
}

export async function cityHasNotices(
  city: City,
  { kind, cache = CACHE.items }: Options = {},
): Promise<boolean> {
  const filter = { near: cityArea(city) }
  try {
    const pages = await Promise.all(
      (kind ? [kind] : ITEM_KINDS).map((it) => fetchItems(it, { filter, first: 1 }, cache(it))),
    )
    return pages.some((page) => page.items.length > 0)
  } catch (error) {
    console.error(`notices query for ${city.slug} failed, treating the city as not empty`, error)
    return true
  }
}
