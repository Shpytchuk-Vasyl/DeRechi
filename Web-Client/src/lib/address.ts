type Component = { long_name: string; types: string[] }

export function addressWithoutCountry(formatted: string, components: Component[]): string {
  const country = components.find((component) => component.types.includes("country"))?.long_name
  const parts = formatted.split(", ")
  const at = country ? parts.lastIndexOf(country) : -1
  return at > 0 ? parts.slice(0, at).join(", ") : formatted
}

const POSTCODE = /^\d{5}$/
const HOUSE_NUMBER = /^\d+\p{L}?(\/\d+)?$/u

function placeParts(place: string): string[] {
  return place
    .split(",")
    .map((part) => part.trim())
    .filter((part) => part && !POSTCODE.test(part))
}

export function tidyPlace(place: string): string {
  return placeParts(place).join(", ")
}

export function shortPlace(place: string): string {
  const parts = placeParts(place)
  return parts.find((part) => !HOUSE_NUMBER.test(part)) ?? parts[0] ?? place.trim()
}
