export type City = {
  slug: string
  lat: number
  lon: number
  radiusKm: number
}

export const CITIES: readonly City[] = [
  { slug: "kyiv", lat: 50.4501, lon: 30.5234, radiusKm: 20 },
  { slug: "lviv", lat: 49.8397, lon: 24.0297, radiusKm: 12 },
  { slug: "kharkiv", lat: 49.9935, lon: 36.2304, radiusKm: 15 },
  { slug: "odesa", lat: 46.4825, lon: 30.7233, radiusKm: 15 },
  { slug: "dnipro", lat: 48.4647, lon: 35.0462, radiusKm: 15 },
  { slug: "zaporizhzhia", lat: 47.8388, lon: 35.1396, radiusKm: 15 },
  { slug: "vinnytsia", lat: 49.2331, lon: 28.4682, radiusKm: 10 },
  { slug: "lutsk", lat: 50.7472, lon: 25.3254, radiusKm: 10 },
  { slug: "zhytomyr", lat: 50.2547, lon: 28.6587, radiusKm: 10 },
  { slug: "uzhhorod", lat: 48.6208, lon: 22.2879, radiusKm: 10 },
  { slug: "ivano-frankivsk", lat: 48.9226, lon: 24.7111, radiusKm: 10 },
  { slug: "kropyvnytskyi", lat: 48.5079, lon: 32.2623, radiusKm: 10 },
  { slug: "mykolaiv", lat: 46.975, lon: 31.9946, radiusKm: 12 },
  { slug: "poltava", lat: 49.5883, lon: 34.5514, radiusKm: 10 },
  { slug: "rivne", lat: 50.6199, lon: 26.2516, radiusKm: 10 },
  { slug: "sumy", lat: 50.9077, lon: 34.7981, radiusKm: 10 },
  { slug: "ternopil", lat: 49.5535, lon: 25.5948, radiusKm: 10 },
  { slug: "kherson", lat: 46.6354, lon: 32.6169, radiusKm: 12 },
  { slug: "khmelnytskyi", lat: 49.4229, lon: 26.9871, radiusKm: 10 },
  { slug: "cherkasy", lat: 49.4444, lon: 32.0598, radiusKm: 10 },
  { slug: "chernivtsi", lat: 48.2915, lon: 25.9403, radiusKm: 10 },
  { slug: "chernihiv", lat: 51.4982, lon: 31.2893, radiusKm: 10 },
]

const EARTH_RADIUS_KM = 6371

export function cityBySlug(slug: string): City | undefined {
  return CITIES.find((city) => city.slug === slug)
}

export function nearestCity(lat: number, lon: number): City | undefined {
  let nearest: City | undefined
  let nearestKm = Number.POSITIVE_INFINITY
  for (const city of CITIES) {
    const km = distanceKm(lat, lon, city.lat, city.lon)
    if (km <= city.radiusKm && km < nearestKm) {
      nearest = city
      nearestKm = km
    }
  }
  return nearest
}

export function cityOf(place: { lat?: number | null; lon?: number | null }): City | undefined {
  return place.lat != null && place.lon != null ? nearestCity(place.lat, place.lon) : undefined
}

export function withCity(place: string, city: string | undefined): string {
  if (!city || place.toLocaleLowerCase().includes(city.toLocaleLowerCase())) {
    return place
  }
  return `${place}, ${city}`
}

export function cityArea(city: City): { lat: number; lon: number; radiusKm: number } {
  return { lat: city.lat, lon: city.lon, radiusKm: city.radiusKm }
}

function distanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLon = (lon2 - lon1) * rad
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a))
}
