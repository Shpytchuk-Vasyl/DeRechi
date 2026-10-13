import { describe, expect, it, vi } from "vitest"

const { fetchItems } = vi.hoisted(() => ({ fetchItems: vi.fn() }))

vi.mock("@/lib/env/client", () => ({
  clientEnv: { NEXT_PUBLIC_SITE_URL: "https://derechi.example/" },
}))
vi.mock("@/api/items", () => ({ fetchItems }))

const { default: sitemap } = await import("./sitemap")

const today = new Date().toISOString().slice(0, 10)

describe("sitemap", () => {
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
})
