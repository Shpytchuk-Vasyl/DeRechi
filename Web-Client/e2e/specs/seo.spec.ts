import { expect, test } from "../fixtures/base"
import { ItemDetail } from "../fixtures/pages"
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

test.describe("stale item pages", () => {
  test("an item older than 14 days is noindex (but followed)", async ({ page, go, t, shared }) => {
    const item = shared.items.lostStale
    await go(item.path)
    await expect(new ItemDetail(page, t, "lost").heading(item.title)).toBeVisible()

    await expect(robotsMeta(page)).toHaveCount(1)
    await expect(robotsMeta(page)).toHaveAttribute("content", /(^|,\s*)noindex(,|$)/)
    await expect(robotsMeta(page)).not.toHaveAttribute("content", /nofollow/)
  })
})
