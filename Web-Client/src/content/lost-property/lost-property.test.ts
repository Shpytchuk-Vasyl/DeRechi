import { describe, expect, it } from "vitest"
import { locales } from "@/i18n/routing"
import { CITIES } from "@/lib/cities"
import { lostProperty, lostPropertyTexts, NATIONAL, PLACES, UNTRUSTED_HOSTS } from "./index"

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

function links(): string[] {
  return [
    NATIONAL.railway,
    NATIONAL.bus,
    NATIONAL.finder,
    ...PLACES.flatMap((place) => [place.url ?? "", place.telegram ?? "", ...place.sources]),
  ].filter((link) => link.startsWith("http"))
}

describe("lost property", () => {
  it("lists places only for cities that have a page, each id once", () => {
    const slugs = new Set(CITIES.map((city) => city.slug))
    for (const place of PLACES) expect(slugs.has(place.city), place.id).toBe(true)
    expect(new Set(PLACES.map((place) => place.id)).size).toBe(PLACES.length)
  })

  it("names and describes every place in every locale, and nothing else", () => {
    const ids = PLACES.map((place) => place.id).sort()
    for (const locale of locales) {
      const texts = lostPropertyTexts(locale)
      expect(Object.keys(texts.places).sort(), locale).toEqual(ids)
      for (const id of ids) {
        expect(texts.places[id].name.trim(), `${locale}/${id}`).not.toBe("")
        expect(texts.places[id].text.trim(), `${locale}/${id}`).not.toBe("")
      }
    }
  })

  it("keeps the same placeholders in every locale", () => {
    const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1])
    const reference = lostPropertyTexts("en")
    for (const locale of locales) {
      const texts = lostPropertyTexts(locale)
      expect(placeholders(texts.title), locale).toEqual(placeholders(reference.title))
      expect(placeholders(texts.leadEmpty), locale).toEqual(placeholders(reference.leadEmpty))
      expect(placeholders(texts.checked), locale).toEqual(placeholders(reference.checked))
    }
  })

  it("dials what it shows: a Ukrainian number in full international form", () => {
    for (const place of PLACES) {
      if (!place.phone) continue
      expect(place.phone.tel, place.id).toMatch(/^\+380\d{9}$/)
      expect(place.phone.label.replace(/\D/g, ""), place.id).toBe(
        place.phone.tel.replace(/^\+38/, "").replace(/\D/g, ""),
      )
    }
  })

  it("records when and against what every entry was checked", () => {
    expect(NATIONAL.checked).toMatch(ISO_DATE)
    for (const place of PLACES) {
      expect(place.checked, place.id).toMatch(ISO_DATE)
      expect(place.sources.length, place.id).toBeGreaterThan(0)
      // A web page, or for a city with none, the research file that records the search.
      for (const source of place.sources) {
        expect(source, place.id).toMatch(/^(https?:\/\/|__docs\/research\/lost-property\/)/)
      }
    }
  })

  it("links only web pages, and never a host known to be untrusted", () => {
    for (const link of links()) {
      const url = new URL(link)
      expect(["http:", "https:"], link).toContain(url.protocol)
      expect(UNTRUSTED_HOSTS, link).not.toContain(url.hostname.replace(/^www\./, ""))
    }
  })

  it("has something local for every city with a page", () => {
    for (const city of CITIES) {
      expect(lostProperty("uk", city.slug).places.length, city.slug).toBeGreaterThan(0)
    }
  })

  it("shows a place as confirmed only when it has an official page to point to", () => {
    for (const place of PLACES) {
      if (!place.needsCheck) expect(place.url, place.id).toBeDefined()
    }
  })

  it("gives a city its own places and the oldest check date among them", () => {
    const kyiv = lostProperty("uk", "kyiv")
    expect(kyiv.places.map((place) => place.id)).toEqual(["kyiv-metro", "kyiv-kpt"])
    expect(kyiv.places[0].name).toBe(lostPropertyTexts("uk").places["kyiv-metro"].name)
    expect(kyiv.checked).toMatch(ISO_DATE)

    expect(lostProperty("uk", "atlantis").places).toEqual([])
  })

  it("falls back to the default locale for an unknown one", () => {
    expect(lostPropertyTexts("xx")).toBe(lostPropertyTexts("en"))
  })
})
