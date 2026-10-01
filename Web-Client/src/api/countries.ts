import "server-only"
import { graphqlRequest } from "@/graphql/client"
import { CountriesQuery } from "@/graphql/documents"
import type { Country } from "@/lib/intl/country"

const COUNTRIES_REVALIDATE = 3600

const OUTAGE_COUNTRIES: Country[] = [{ code: "UA", currency: "UAH" }]

export async function fetchCountries(): Promise<Country[]> {
  try {
    const { countries } = await graphqlRequest(
      CountriesQuery,
      {},
      { revalidate: COUNTRIES_REVALIDATE, tags: ["countries"] },
    )
    return countries.length > 0 ? countries : OUTAGE_COUNTRIES
  } catch (error) {
    console.error("countries query failed, falling back to the default country", error)
    return OUTAGE_COUNTRIES
  }
}
