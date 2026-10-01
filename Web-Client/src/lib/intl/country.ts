export type Country = {
  code: string
  currency: string
}

export type Money = {
  amount: number
  currency: string
}

export const COUNTRY_COOKIE = "DERECHI_COUNTRY"

export const COUNTRY_COOKIE_MAX_AGE = 60 * 60 * 24 * 365

export const GEO_COUNTRY_HEADER = "x-vercel-ip-country"

const ALPHA2 = /^[A-Za-z]{2}$/

export function normaliseCountryCode(value: string | null | undefined): string | null {
  const code = value?.trim()
  return code && ALPHA2.test(code) ? code.toUpperCase() : null
}

export function isSupported(countries: readonly Country[], code: string | null): code is string {
  return code !== null && countries.some((country) => country.code === code)
}

export function pickCountry(
  countries: readonly Country[],
  ...candidates: (string | null | undefined)[]
): Country {
  for (const candidate of candidates) {
    const code = normaliseCountryCode(candidate)
    const match = countries.find((country) => country.code === code)
    if (match) return match
  }
  const first = countries[0]
  if (!first) throw new Error("Client-API reports no supported countries")
  return first
}

export function currencyOf(
  countries: readonly Country[],
  code: string | null | undefined,
): string | null {
  const normalised = normaliseCountryCode(code)
  return countries.find((country) => country.code === normalised)?.currency ?? null
}

export function currencies(countries: readonly Country[]): string[] {
  return [...new Set(countries.map((country) => country.currency))]
}

export function countryName(locale: string, code: string): string {
  try {
    return new Intl.DisplayNames([locale], { type: "region" }).of(code) ?? code
  } catch {
    return code
  }
}

export function readCountryCookie(cookieHeader: string): string | null {
  const match = cookieHeader
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${COUNTRY_COOKIE}=`))
  return match
    ? normaliseCountryCode(decodeURIComponent(match.slice(COUNTRY_COOKIE.length + 1)))
    : null
}
