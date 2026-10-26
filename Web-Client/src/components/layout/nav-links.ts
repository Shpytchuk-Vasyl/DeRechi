import { paths } from "@/i18n/paths"
import { cityInPath } from "@/lib/cities"

export function navLinks(pathname: string) {
  const city = cityInPath(pathname)
  return {
    city: city ? paths.city(city) : undefined,
    lost: city ? paths.cityList(city, "lost") : paths.list("lost"),
    found: city ? paths.cityList(city, "found") : paths.list("found"),
  }
}
