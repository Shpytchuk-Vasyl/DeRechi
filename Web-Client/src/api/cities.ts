import "server-only"
import { fetchItems } from "@/api/items"
import { type City, cityArea } from "@/lib/cities"
import { ITEM_KINDS, type ItemKind } from "@/lib/kinds"

export async function cityHasNotices(city: City, kind?: ItemKind): Promise<boolean> {
  const filter = { near: cityArea(city) }
  try {
    const pages = await Promise.all(
      (kind ? [kind] : ITEM_KINDS).map((it) => fetchItems(it, { filter, first: 1 })),
    )
    return pages.some((page) => page.items.length > 0)
  } catch (error) {
    console.error(`notices query for ${city.slug} failed, treating the city as not empty`, error)
    return true
  }
}
