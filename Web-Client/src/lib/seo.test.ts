import { describe, expect, it, vi } from "vitest"

vi.mock("@/lib/env/client", () => ({
  clientEnv: { NEXT_PUBLIC_SITE_URL: "https://derechi.example/" },
}))

const { absoluteUrl, isStale, pageAlternates, sitemapIds, snippet } = await import("./seo")

describe("seo", () => {
  it("points every locale and x-default at the same path", () => {
    expect(pageAlternates("uk", "/lost/7")).toEqual({
      canonical: "/uk/lost/7",
      languages: {
        en: "/en/lost/7",
        uk: "/uk/lost/7",
        pl: "/pl/lost/7",
        de: "/de/lost/7",
        fr: "/fr/lost/7",
        "x-default": "/en/lost/7",
      },
    })
  })

  it("builds absolute URLs without a double slash after the site URL", () => {
    expect(absoluteUrl("pl", "/found")).toBe("https://derechi.example/pl/found")
  })

  it("marks a notice stale after thirty days", () => {
    const now = new Date("2026-10-02T12:00:00Z")

    expect(isStale("2026-09-02T12:00:00Z", now)).toBe(false)
    expect(isStale("2026-09-01T12:00:00Z", now)).toBe(true)
  })

  it("keeps a short description as it is, with whitespace collapsed", () => {
    expect(snippet("  Black\n backpack  ")).toBe("Black backpack")
  })

  it("cuts a long description at a word boundary and marks the cut", () => {
    const text = `${"word ".repeat(40)}end`
    const cut = snippet(text, 20)

    expect(cut).toBe("word word word…")
    expect(cut.length).toBeLessThanOrEqual(20)
  })

  it("cuts mid-word when there is no space in the first half", () => {
    expect(snippet("x".repeat(30), 10)).toBe(`${"x".repeat(9)}…`)
  })

  it("splits the sitemap into static pages and one chunk per kind and month", () => {
    expect(sitemapIds()).toEqual([
      { id: "pages" },
      { id: "lost-0" },
      { id: "lost-1" },
      { id: "found-0" },
      { id: "found-1" },
    ])
  })
})
