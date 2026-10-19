import { describe, expect, it } from "vitest"
import { locales } from "@/i18n/routing"
import { GUIDE_META, GUIDE_SLUGS, guideByCategory, guideTexts, isGuideSlug } from "./index"

describe("guides", () => {
  it("has every guide in every locale, with the same shape", () => {
    const shape = (locale: string) =>
      GUIDE_SLUGS.map((slug) => {
        const doc = guideTexts(locale).guides[slug]
        return {
          slug,
          sections: doc.sections.map((section) => ({
            id: section.id,
            blocks: section.body?.length ?? 0,
            steps: section.steps?.length ?? 0,
          })),
          faq: doc.faq.length,
        }
      })
    const reference = shape("en")
    for (const locale of locales) {
      expect(shape(locale), locale).toEqual(reference)
      expect(Object.keys(guideTexts(locale).guides).sort()).toEqual([...GUIDE_SLUGS].sort())
    }
  })

  it("gives every section a unique id, so the contents links land", () => {
    for (const locale of locales) {
      for (const slug of GUIDE_SLUGS) {
        const ids = guideTexts(locale).guides[slug].sections.map((section) => section.id)
        expect(new Set(ids).size, `${locale}/${slug}`).toBe(ids.length)
      }
    }
  })

  it("relates guides only to guides that exist", () => {
    for (const slug of GUIDE_SLUGS) {
      for (const related of GUIDE_META[slug].related) {
        expect(isGuideSlug(related)).toBe(true)
        expect(related).not.toBe(slug)
      }
    }
  })

  it("finds a guide by its notice category", () => {
    expect(guideByCategory("DOCUMENTS")).toBe("documents")
    expect(guideByCategory("ANIMALS")).toBe("animals")
    expect(guideByCategory("OTHER")).toBeUndefined()
  })

  it("falls back to the default locale for an unknown one", () => {
    expect(guideTexts("xx")).toBe(guideTexts("en"))
  })
})
