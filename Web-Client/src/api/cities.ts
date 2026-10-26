import "server-only"
import { fetchItems, type ItemKind } from "@/api/items"
import { type City, cityArea } from "@/lib/cities"

const KINDS: ItemKind[] = ["lost", "found"]

export async function cityHasNotices(city: City, kind?: ItemKind): Promise<boolean> {
  const filter = { near: cityArea(city) }
  try {
    const pages = await Promise.all(
      (kind ? [kind] : KINDS).map((it) => fetchItems(it, { filter, first: 1 })),
    )
    return pages.some((page) => page.items.length > 0)
  } catch (error) {
    console.error(`notices query for ${city.slug} failed, treating the city as not empty`, error)
    return true
  }
}
