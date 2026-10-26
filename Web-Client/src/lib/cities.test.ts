import { describe, expect, it } from "vitest"
import en from "../../messages/en.json"
import { CITIES, cityBySlug, cityListKind, nearestCity, withCity } from "./cities"

describe("cities", () => {
  it("has a name for every city in the bundles", () => {
    expect(Object.keys(en.city).sort()).toEqual(CITIES.map((city) => city.slug).sort())
  })

  it("finds a city by its slug and nothing for an unknown one", () => {
    expect(cityBySlug("lviv")?.lat).toBe(49.8397)
    expect(cityBySlug("warsaw")).toBeUndefined()
  })

  it("puts a place on the edge of a city into that city", () => {
    expect(nearestCity(49.442, 26.931)?.slug).toBe("khmelnytskyi")
    expect(nearestCity(46.427, 30.718)?.slug).toBe("odesa")
  })

  it("leaves a place outside every city's radius without a city", () => {
    expect(nearestCity(49.0, 25.0)).toBeUndefined()
  })

  it("adds the city after a place that does not name it", () => {
    expect(withCity("Привокзальний ринок", "Львів")).toBe("Привокзальний ринок, Львів")
  })

  it("leaves a place that already names its city, or has none, as it is", () => {
    expect(withCity("Одеса", "Одеса")).toBe("Одеса")
    expect(withCity("вулиця Шевченка, Львів", "Львів")).toBe("вулиця Шевченка, Львів")
    expect(withCity("Гречани", undefined)).toBe("Гречани")
    expect(withCity("ТЦ (Львів)", "Львів")).toBe("ТЦ (Львів)")
    expect(withCity("Івано-Франківськ", "Івано-Франківськ")).toBe("Івано-Франківськ")
  })

  it("adds the city after a place that only contains it inside a longer word", () => {
    expect(withCity("Тернопільська школа № 11", "Тернопіль")).toBe(
      "Тернопільська школа № 11, Тернопіль",
    )
    expect(withCity("вулиця Київська", "Київ")).toBe("вулиця Київська, Київ")
  })

  it("keeps the city areas apart, so a place belongs to at most one", () => {
    for (const city of CITIES) {
      expect(nearestCity(city.lat, city.lon)?.slug, city.slug).toBe(city.slug)
    }
  })

  it("tells a city's list from other paths", () => {
    expect(cityListKind("/lviv/found")).toBe("found")
    expect(cityListKind("/kyiv/lost")).toBe("lost")
    expect(cityListKind("/warsaw/found")).toBeUndefined()
    expect(cityListKind("/lviv")).toBeUndefined()
    expect(cityListKind("/found/87")).toBeUndefined()
    expect(cityListKind("/lviv/found/87")).toBeUndefined()
  })
})
