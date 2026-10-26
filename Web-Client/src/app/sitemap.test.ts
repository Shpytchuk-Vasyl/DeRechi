import { beforeEach, describe, expect, it, vi } from "vitest"

const { fetchItems } = vi.hoisted(() => ({ fetchItems: vi.fn() }))

vi.mock("@/lib/env/client", () => ({
  clientEnv: { NEXT_PUBLIC_SITE_URL: "https://derechi.example/" },
}))
vi.mock("@/api/items", () => ({ fetchItems }))
vi.mock("server-only", () => ({}))

const { default: sitemap } = await import("./sitemap")

const today = new Date().toISOString().slice(0, 10)

describe("sitemap", () => {
  beforeEach(() => fetchItems.mockReset())

  it("lists a notice once, by its Ukrainian page and without hreflang", async () => {
    fetchItems.mockResolvedValue({ items: [{ id: "7", date: today }], hasNextPage: false })

    const entries = await sitemap({ id: Promise.resolve("lost-0") })

    expect(entries).toEqual([
      { url: "https://derechi.example/uk/lost/7", lastModified: today, priority: 0.6 },
    ])
  })

  it("gives a month without notices the list page instead of an empty file", async () => {
    fetchItems.mockResolvedValue({ items: [], hasNextPage: false })

    const entries = await sitemap({ id: Promise.resolve("found-1") })

    expect(entries.map((entry) => entry.url)).toEqual(["https://derechi.example/en/found"])
  })

  it("lists a city's page among the pages only while it has notices", async () => {
    fetchItems.mockImplementation(
      async (_kind: string, query?: { filter?: { near?: { lat: number } } }) => ({
        items: query?.filter?.near?.lat === 49.8397 ? [{ id: "1", date: today }] : [],
        hasNextPage: false,
      }),
    )

    const urls = (await sitemap({ id: Promise.resolve("pages") })).map((entry) => entry.url)

    expect(urls).toContain("https://derechi.example/en/lviv")
    expect(urls).not.toContain("https://derechi.example/en/kyiv")
  })

  it("lists a city's list only for the kind it has notices of", async () => {
    fetchItems.mockImplementation(
      async (kind: string, query?: { filter?: { near?: { lat: number } } }) => ({
        items:
          kind === "found" && query?.filter?.near?.lat === 49.8397
            ? [{ id: "1", date: today }]
            : [],
        hasNextPage: false,
      }),
    )

    const urls = (await sitemap({ id: Promise.resolve("pages") })).map((entry) => entry.url)

    expect(urls).toContain("https://derechi.example/en/lviv")
    expect(urls).toContain("https://derechi.example/en/lviv/found")
    expect(urls).not.toContain("https://derechi.example/en/lviv/lost")
    expect(urls).not.toContain("https://derechi.example/en/kyiv/found")
  })
})
