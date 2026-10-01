"use server"

import { type KnownPlace, MAX_PLACE_QUERY, searchKnownPlaces } from "@/api/places"

const MIN_QUERY = 2

export async function searchPlaces(query: string): Promise<KnownPlace[]> {
  const name = query.trim().slice(0, MAX_PLACE_QUERY)
  if (name.length < MIN_QUERY) return []

  return searchKnownPlaces(name)
}
