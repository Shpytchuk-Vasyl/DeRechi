import { expect, test } from "../fixtures/base"
import { LOCALES } from "../support/data"
import { env } from "../support/env"
import { expectAlternates, GLOBAL_NOINDEX, robotsMeta } from "../support/seo"
import { siteUrl } from "../support/site"

const KEY_PAGES = [
  "",
  "/lost",
  "/found",
  "/report/lost",
  "/report/found",
  "/safety",
  "/guide",
  "/guide/documents",
  "/terms",
  "/privacy",
]

test.describe("SEO", () => {
  test("home: absolute canonical, hreflang for every language, noindex, no Link header", async ({
    page,
    go,
    appLocale,
  }) => {
    const response = await go("/")
    expect(response?.status()).toBe(200)

    await expectAlternates(page, appLocale, "")

    expect(response?.headers().link ?? "").not.toMatch(/hreflang/i)

    await expect(robotsMeta(page)).toHaveAttribute("content", GLOBAL_NOINDEX)
  })

  test("robots.txt is served and disallows everything", async ({ request }) => {
    const response = await request.get("/robots.txt")
    expect(response.status()).toBe(200)
    expect(response.headers()["content-type"]).toMatch(/text\/plain/)
    const text = await response.text()
    expect(text).toMatch(/user-agent:\s*\*/i)
    expect(text).toMatch(/^disallow:\s*\/\s*$/im)
    expect(text).not.toMatch(/^allow:/im)
  })

  test("/sitemap/pages.xml lists the key pages with every language, and they answer 200", async ({
    request,
    page,
    go,
  }) => {
    const response = await request.get("/sitemap/pages.xml")
    expect(response.status()).toBe(200)
    expect(response.headers()["content-type"]).toMatch(/xml/)
    const xml = await response.text()

    await go("/")
    const parsed = await page.evaluate((source) => {
      const doc = new DOMParser().parseFromString(source, "application/xml")
      const XHTML = "http://www.w3.org/1999/xhtml"
      return {
        error: doc.getElementsByTagName("parsererror").length > 0,
        root: doc.documentElement.localName,
        urls: Array.from(doc.getElementsByTagName("url")).map((url) => ({
          loc: url.getElementsByTagName("loc")[0]?.textContent ?? "",
          alternates: Array.from(url.getElementsByTagNameNS(XHTML, "link")).map(
            (link) => `${link.getAttribute("hreflang")} ${link.getAttribute("href")}`,
          ),
        })),
      }
    }, xml)

    expect(parsed.error).toBe(false)
    expect(parsed.root).toBe("urlset")
    const locs = parsed.urls.map((url) => url.loc)
    expect(locs).toEqual(expect.arrayContaining(KEY_PAGES.map((path) => siteUrl("en", path))))

    for (const path of KEY_PAGES) {
      const url = parsed.urls.find((it) => it.loc === siteUrl("en", path))
      expect(url?.alternates.sort(), `alternates of ${path || "/"}`).toEqual(
        [
          ...LOCALES.map((language) => `${language} ${siteUrl(language, path)}`),
          `x-default ${siteUrl("en", path)}`,
        ].sort(),
      )
      const answer = await request.get(new URL(`/en${path}`, env.baseURL).toString())
      expect(answer.status(), `${path || "/"} answers`).toBe(200)
    }
  })
})

test.describe("item pages", () => {
  for (const key of ["foundPhoto", "lostNoPhoto"] as const) {
    test(`${key}: og:image is a 1200×630 PNG of the notice`, async ({
      page,
      go,
      request,
      shared,
    }) => {
      await go(shared.items[key].path)

      const image = page.locator('meta[property="og:image"]')
      await expect(image).toHaveCount(1)
      await expect(page.locator('meta[property="og:image:width"]')).toHaveAttribute(
        "content",
        "1200",
      )
      await expect(page.locator('meta[property="og:image:height"]')).toHaveAttribute(
        "content",
        "630",
      )

      const url = new URL((await image.getAttribute("content")) ?? "")
      const response = await request.get(new URL(url.pathname + url.search, env.baseURL).toString())
      expect(response.status()).toBe(200)
      expect(response.headers()["content-type"]).toBe("image/png")
    })
  }
})
