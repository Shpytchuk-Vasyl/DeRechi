import { shortPlace, tidyPlace } from "@/lib/address"
import { withCity } from "@/lib/cities"
import { snippet } from "@/lib/seo"

const PLACE_LIMIT = 40

export function noticePlace(place: string, city: string | undefined): string {
  return withCity(snippet(shortPlace(tidyPlace(place)), PLACE_LIMIT), city)
}
