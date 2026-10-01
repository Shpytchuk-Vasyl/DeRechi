import "server-only"
import { graphqlRequest } from "@/graphql/client"
import { PlacesQuery } from "@/graphql/documents"

export type KnownPlace = {
  id: string
  name: string
  lat: number
  lon: number
  countryCode: string
}

export const MAX_PLACE_QUERY = 100

const SUGGESTIONS = 8

const PLACES_REVALIDATE = 60

export async function searchKnownPlaces(name: string): Promise<KnownPlace[]> {
  const { places } = await graphqlRequest(
    PlacesQuery,
    { name, first: SUGGESTIONS },
    { revalidate: PLACES_REVALIDATE, tags: ["places"] },
  )

  return places.edges.flatMap(({ node }) =>
    node.lat != null && node.lon != null
      ? [
          {
            id: node.id,
            name: node.name,
            lat: node.lat,
            lon: node.lon,
            countryCode: node.countryCode,
          },
        ]
      : [],
  )
}
