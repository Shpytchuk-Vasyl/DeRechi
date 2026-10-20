import { describe, expect, it } from "vitest"
import { addressWithoutCountry, shortPlace, tidyPlace } from "./address"

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

const SCHOOL =
  "Тернопільська загальноосвітня школа І-III ступенів № 11, 46024, вулиця Генерала Мирона Тарнавського,"

describe("tidyPlace", () => {
  it("drops the postcode, empty parts and stray commas", () => {
    expect(tidyPlace(SCHOOL)).toBe(
      "Тернопільська загальноосвітня школа І-III ступенів № 11, вулиця Генерала Мирона Тарнавського",
    )
    expect(tidyPlace(" вулиця Наукова ,, Львів ")).toBe("вулиця Наукова, Львів")
  })

  it("drops house numbers and repeated parts", () => {
    expect(tidyPlace("Школа №1, Корецька ЗШ І-ІІІ ст. №1, 60, 60, Корець")).toBe(
      "Школа №1, Корецька ЗШ І-ІІІ ст. №1, Корець",
    )
    expect(tidyPlace("вулиця Наукова, 5а, Львів, львів")).toBe("вулиця Наукова, Львів")
  })

  it("keeps a place that is only a number", () => {
    expect(tidyPlace("17")).toBe("17")
  })

  it("keeps a clean place as it is", () => {
    expect(tidyPlace("Привокзальний ринок")).toBe("Привокзальний ринок")
  })
})

describe("shortPlace", () => {
  it("takes the first part of the address", () => {
    expect(shortPlace(SCHOOL)).toBe("Тернопільська загальноосвітня школа І-III ступенів № 11")
    expect(shortPlace("вулиця Чумацька, Біла")).toBe("вулиця Чумацька")
  })

  it("skips a leading house number or postcode", () => {
    expect(shortPlace("12а, вулиця Наукова, Львів")).toBe("вулиця Наукова")
    expect(shortPlace("5/2, вулиця Наукова")).toBe("вулиця Наукова")
    expect(shortPlace("79000, Львів")).toBe("Львів")
  })

  it("keeps a place that is only a number rather than returning nothing", () => {
    expect(shortPlace("17")).toBe("17")
  })
})
