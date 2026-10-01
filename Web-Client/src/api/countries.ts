import "server-only"
import { graphqlRequest } from "@/graphql/client"
import { CountriesQuery } from "@/graphql/documents"
import type { Country } from "@/lib/intl/country"

const COUNTRIES_REVALIDATE = 604800

export async function fetchCountries(): Promise<Country[]> {
  const { countries } = await graphqlRequest(
    CountriesQuery,
    {},
    { revalidate: COUNTRIES_REVALIDATE, tags: ["countries"] },
  )
  return countries
}
