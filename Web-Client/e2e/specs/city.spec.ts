import { expect, test } from "../fixtures/base"
import { ItemCard } from "../fixtures/pages"
import { HomePage } from "../fixtures/pages/home-page"
import { env } from "../support/env"
import { readJsonLd } from "../support/seo"

test.describe("city pages", () => {
  test("a city page names its city and shows found and lost notices near it", async ({
    page,
    go,
    t,
  }) => {
    await go("/lviv")
    const where = t("city.lviv.in")

    await expect(
      page.getByRole("heading", { level: 1, name: t("cityPage.heroTitle", { in: where }) }),
    ).toBeVisible()
    await expect(
      page.getByRole("heading", { level: 2, name: t("cityPage.found", { in: where }) }),
    ).toBeVisible()
    await expect(
      page.getByRole("heading", { level: 2, name: t("cityPage.lost", { in: where }) }),
    ).toBeVisible()
    await expect(new ItemCard(page, t).all().first()).toBeVisible()
  })

  test("the search on a city page looks around the city", async ({ page, go, t, appLocale }) => {
    await go("/lviv")
    const box = new HomePage(page, t).searchBox()

    await box.fill(t("category.KEYS"))
    await box.press("Enter")

    await expect(page).toHaveURL(new RegExp(`/${appLocale}/found\\?`))
    const params = new URL(page.url()).searchParams
    expect(params.get("search")).toBe(t("category.KEYS"))
    expect([params.get("lat"), params.get("lon")]).toEqual(["49.8397", "24.0297"])
  })

  test("a city page has the site's WebSite and a CollectionPage about the city", async ({
    page,
    go,
    t,
  }) => {
    await go("/lviv")
    const blocks = await readJsonLd(page)

    expect(blocks.find((block) => block["@type"] === "WebSite")).toMatchObject({
      name: t("app.name"),
      publisher: { "@type": "Organization" },
    })
    expect(blocks.find((block) => block["@type"] === "CollectionPage")).toMatchObject({
      name: t("cityPage.title", { in: t("city.lviv.in") }),
      about: { "@type": "City", name: t("city.lviv.name") },
      breadcrumb: { "@type": "BreadcrumbList" },
    })
  })

  test("a slug outside the city list answers 404", async ({ request, appLocale }) => {
    const response = await request.get(new URL(`/${appLocale}/warsaw`, env.baseURL).toString())
    expect(response.status()).toBe(404)
  })

  test("a city on the home map opens its page", async ({ page, go, t }) => {
    await go("/")
    await page.getByRole("link", { name: t("city.kyiv.name"), exact: true }).click()

    await expect(page).toHaveURL(/\/kyiv$/)
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: t("cityPage.heroTitle", { in: t("city.kyiv.in") }),
      }),
    ).toBeVisible()
  })

  test("a notice page links to the page of its city", async ({ page, go, t, shared }) => {
    await go(shared.items.lostFull.path)
    await page.getByRole("link", { name: t("item.cityLink", { in: t("city.lviv.in") }) }).click()

    await expect(page).toHaveURL(/\/lviv$/)
  })
})

test.describe("city picker on a phone", { tag: "@mobile-only" }, () => {
  test("choosing a city under the map opens its page", async ({ page, go, t }) => {
    await go("/")
    await page.getByRole("combobox", { name: t("home.citiesPick") }).click()
    await page.getByRole("option", { name: t("city.odesa.name"), exact: true }).click()

    await expect(page).toHaveURL(/\/odesa$/)
  })
})
