import "server-only"
import { headers } from "next/headers"
import { type City, nearestCity } from "@/lib/cities"

export const GEO_LATITUDE_HEADER = "x-vercel-ip-latitude"

export const GEO_LONGITUDE_HEADER = "x-vercel-ip-longitude"

function coordinate(value: string | null): number | undefined {
  if (!value) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export async function requestCity(): Promise<City | undefined> {
  const requestHeaders = await headers()
  const lat = coordinate(requestHeaders.get(GEO_LATITUDE_HEADER))
  const lon = coordinate(requestHeaders.get(GEO_LONGITUDE_HEADER))
  return lat !== undefined && lon !== undefined ? nearestCity(lat, lon) : undefined
}
