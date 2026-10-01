import { describe, expect, it } from "vitest"
import {
  countryName,
  currencies,
  currencyOf,
  normaliseCountryCode,
  pickCountry,
  readCountryCookie,
} from "./country"

const COUNTRIES = [
  { code: "UA", currency: "UAH" },
  { code: "PL", currency: "PLN" },
  { code: "DE", currency: "EUR" },
  { code: "FR", currency: "EUR" },
]

describe("country", () => {
  it("normalises codes the way Client-API stores them", () => {
    expect(normaliseCountryCode(" pl ")).toBe("PL")
    expect(normaliseCountryCode("POL")).toBeNull()
    expect(normaliseCountryCode(undefined)).toBeNull()
  })

  it("picks the first supported candidate and falls back to the first configured country", () => {
    expect(pickCountry(COUNTRIES, null, "us", "de").code).toBe("DE")
    expect(pickCountry(COUNTRIES, "US", "XX").code).toBe("UA")
    expect(pickCountry(COUNTRIES).code).toBe("UA")
  })

  it("derives the currency from the country list, not from a table of its own", () => {
    expect(currencyOf(COUNTRIES, "pl")).toBe("PLN")
    expect(currencyOf(COUNTRIES, "US")).toBeNull()
    expect(currencies(COUNTRIES)).toEqual(["UAH", "PLN", "EUR"])
  })

  it("names countries from CLDR in the viewer's language", () => {
    expect(countryName("uk", "PL")).toBe("Польща")
    expect(countryName("en", "DE")).toBe("Germany")
  })

  it("reads its own cookie out of a cookie header", () => {
    expect(readCountryCookie("DERECHI_LOCALE=uk; DERECHI_COUNTRY=pl")).toBe("PL")
    expect(readCountryCookie("DERECHI_LOCALE=uk")).toBeNull()
  })
})
