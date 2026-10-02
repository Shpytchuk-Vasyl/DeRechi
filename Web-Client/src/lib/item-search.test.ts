import { describe, expect, it } from "vitest"
import { hasActiveFilters, parseItemSearch, toFilter, toQuery } from "./item-search"

const categories = [
  { id: "1", key: "DOCUMENTS" },
  { id: "6", key: "KEYS" },
]

describe("parseItemSearch", () => {
  it("reads a full query string", () => {
    expect(
      parseItemSearch({
        search: " keys ",
        category: "KEYS",
        dateFrom: "2026-09-01",
        dateTo: "2026-09-30",
        lat: "49.842",
        lon: "24.032",
        sort: "TITLE_ASC",
      }),
    ).toEqual({
      search: "keys",
      category: "KEYS",
      dateFrom: "2026-09-01",
      dateTo: "2026-09-30",
      near: { lat: 49.842, lon: 24.032 },
      sort: "TITLE_ASC",
    })
  })

  it("takes the first value of a repeated parameter", () => {
    expect(parseItemSearch({ search: ["wallet", "keys"] }).search).toBe("wallet")
  })

  it("drops blank values instead of searching for an empty string", () => {
    expect(parseItemSearch({ search: "   ", category: "" })).toMatchObject({
      search: undefined,
      category: undefined,
    })
  })

  it("ignores dates that are not ISO, so the API never sees them", () => {
    expect(parseItemSearch({ dateFrom: "01.09.2026", dateTo: "yesterday" })).toMatchObject({
      dateFrom: undefined,
      dateTo: undefined,
    })
  })

  it.each([
    ["only a latitude", { lat: "49.8" }],
    ["a latitude out of range", { lat: "91", lon: "24" }],
    ["a longitude out of range", { lat: "49", lon: "-181" }],
    ["coordinates that are not numbers", { lat: "north", lon: "24" }],
  ])("drops the near filter for %s", (_, raw) => {
    expect(parseItemSearch(raw).near).toBeUndefined()
  })

  it("falls back to the newest first for an unknown sort", () => {
    expect(parseItemSearch({ sort: "PRICE_ASC" }).sort).toBe("DATE_DESC")
    expect(parseItemSearch({}).sort).toBe("DATE_DESC")
  })
})

describe("toFilter", () => {
  it("turns the category key into the id the API filters by", () => {
    expect(toFilter(parseItemSearch({ category: "KEYS" }), categories).categoryId).toBe("6")
  })

  it("drops a category the API does not know", () => {
    expect(toFilter(parseItemSearch({ category: "BOATS" }), categories).categoryId).toBeNull()
  })

  it("sends missing filters as null and adds the radius to a point", () => {
    expect(toFilter(parseItemSearch({ lat: "49.8", lon: "24" }), categories)).toEqual({
      search: null,
      categoryId: null,
      dateFrom: null,
      dateTo: null,
      near: { lat: 49.8, lon: 24, radiusKm: 10 },
    })
  })
})

describe("toQuery", () => {
  it("survives a round trip through the URL", () => {
    const search = parseItemSearch({
      search: "keys",
      category: "KEYS",
      dateFrom: "2026-09-01",
      lat: "49.842",
      lon: "24.032",
      sort: "DATE_ASC",
    })

    expect(parseItemSearch(toQuery(search))).toEqual(search)
  })

  it("leaves the default sort and empty filters out of the URL", () => {
    expect(toQuery(parseItemSearch({ search: "", sort: "DATE_DESC" }))).toEqual({})
  })
})

describe("hasActiveFilters", () => {
  it("does not count the sort or the nearby switch as a filter", () => {
    expect(hasActiveFilters(parseItemSearch({ sort: "TITLE_ASC", lat: "49", lon: "24" }))).toBe(
      false,
    )
    expect(hasActiveFilters(parseItemSearch({ dateTo: "2026-09-30" }))).toBe(true)
  })
})
