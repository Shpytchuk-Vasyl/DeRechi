type Component = { long_name: string; types: string[] }

export function addressWithoutCountry(formatted: string, components: Component[]): string {
  const country = components.find((component) => component.types.includes("country"))?.long_name
  const parts = formatted.split(", ")
  const at = country ? parts.lastIndexOf(country) : -1
  return at > 0 ? parts.slice(0, at).join(", ") : formatted
}
