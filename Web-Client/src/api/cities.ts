import "server-only"
import { fetchItems } from "@/api/items"
import { type City, cityArea } from "@/lib/cities"

export async function cityHasNotices(city: City): Promise<boolean> {
  const filter = { near: cityArea(city) }
  try {
    const [lost, found] = await Promise.all([
      fetchItems("lost", { filter, first: 1 }),
      fetchItems("found", { filter, first: 1 }),
    ])
    return lost.items.length > 0 || found.items.length > 0
  } catch (error) {
    console.error(`notices query for ${city.slug} failed, treating the city as not empty`, error)
    return true
  }
}
