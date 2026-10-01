import "server-only"
import { cookies, headers } from "next/headers"
import { COUNTRY_COOKIE, type Country, GEO_COUNTRY_HEADER, pickCountry } from "@/lib/intl/country"

export async function currentCountry(
  countries: Country[],
  override?: string | null,
): Promise<Country> {
  const [jar, requestHeaders] = await Promise.all([cookies(), headers()])
  return pickCountry(
    countries,
    override,
    jar.get(COUNTRY_COOKIE)?.value,
    requestHeaders.get(GEO_COUNTRY_HEADER),
  )
}
