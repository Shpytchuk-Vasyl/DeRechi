import { describe, expect, it } from "vitest"
import { addressWithoutCountry } from "./address"

const UA = [{ long_name: "Україна", types: ["country", "political"] }]

describe("addressWithoutCountry", () => {
  it("drops the country and the postcode after it", () => {
    expect(
      addressWithoutCountry("вулиця Наукова, 5, Львів, Львівська область, Україна, 79000", UA),
    ).toBe("вулиця Наукова, 5, Львів, Львівська область")
  })

  it("drops a trailing country", () => {
    expect(addressWithoutCountry("Львів, Львівська область, Україна", UA)).toBe(
      "Львів, Львівська область",
    )
  })

  it("keeps the address when the country is not in it or unknown", () => {
    expect(addressWithoutCountry("вулиця Наукова, 5, Львів", UA)).toBe("вулиця Наукова, 5, Львів")
    expect(addressWithoutCountry("вулиця Наукова, 5, Львів, Україна", [])).toBe(
      "вулиця Наукова, 5, Львів, Україна",
    )
  })

  it("keeps an address that is only the country", () => {
    expect(addressWithoutCountry("Україна", UA)).toBe("Україна")
  })
})
