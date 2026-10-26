import { expect, test } from "../fixtures/base"
import { Filters, ItemCard, RouteDialog } from "../fixtures/pages"
import { HomePage } from "../fixtures/pages/home-page"
import { expectListQuery, ListPage } from "../fixtures/pages/list-page"
import { env } from "../support/env"
import { readJsonLd } from "../support/seo"
import { member } from "../support/shared"

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

  test("the search on a city page opens the city's found list", async ({
    page,
    go,
    t,
    appLocale,
  }) => {
    await go("/lviv")
    const box = new HomePage(page, t).searchBox()

    await box.fill(t("category.KEYS"))
    await box.press("Enter")

    await expectListQuery(page, `/${appLocale}/lviv/found`, { search: t("category.KEYS") })
  })

  test("See all on a city page opens the city's own lists", async ({ page, go, t, appLocale }) => {
    const where = t("city.lviv.in")
    for (const kind of ["found", "lost"] as const) {
      await go("/lviv")
      await page
        .getByRole("link", {
          name: t(kind === "found" ? "cityPage.seeAllFound" : "cityPage.seeAllLost"),
        })
        .click()

      await expectListQuery(page, `/${appLocale}/lviv/${kind}`, {})
      await expect(
        page.getByRole("heading", {
          level: 1,
          name: t(kind === "found" ? "list.foundTitleIn" : "list.lostTitleIn", { in: where }),
        }),
      ).toBeVisible()
    }
  })

  test("a city's found list keeps the city's notices and has no Near me switch", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const list = new ListPage(page, t, "found")
    await go(`/lviv/found?search=${shared.search.near}`)

    await expect(list.cards.byTitle(member(shared, "near", "lviv").title)).toBeVisible()
    await expect(list.cards.byTitle(member(shared, "near", "kyiv").title)).toHaveCount(0)
    await expect(list.nearbySwitch()).toHaveCount(0)
  })

  test("filters on a city's list stay in the city", async ({ page, go, t, shared, appLocale }) => {
    const search = shared.search.near
    const filters = new Filters(page, t)
    await go(`/lviv/found?search=${search}`)

    await filters.category("WALLET")
    await filters.apply()

    await expectListQuery(page, `/${appLocale}/lviv/found`, { search, category: "WALLET" })
    await expect(
      new ListPage(page, t, "found").cards.byTitle(member(shared, "near", "lviv").title),
    ).toBeVisible()
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

  test("a slug outside the city list answers 404, and so do its lists", async ({
    request,
    appLocale,
  }) => {
    for (const path of ["/warsaw", "/warsaw/found", "/warsaw/lost"]) {
      const response = await request.get(new URL(`/${appLocale}${path}`, env.baseURL).toString())
      expect(response.status(), path).toBe(404)
    }
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

  test("a city on a guide's map opens the city page at where to turn there", async ({
    page,
    go,
    t,
  }) => {
    await go("/guide/documents")
    await page.getByRole("link", { name: t("city.kyiv.name"), exact: true }).click()

    await expect(page).toHaveURL(/\/kyiv#lost-property$/)
    const section = page.locator("#lost-property")
    await expect(section).toBeFocused()
    await expect(section).toBeInViewport()
  })

  test("with reduced motion a city on a guide's map still lands on where to turn there", async ({
    page,
    go,
    t,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" })
    await go("/guide/documents")
    await page.getByRole("link", { name: t("city.kyiv.name"), exact: true }).click()

    await expect(page).toHaveURL(/\/kyiv#lost-property$/)
    const section = page.locator("#lost-property")
    await expect(section).toBeFocused()
    await expect(section).toBeInViewport()
  })

  test("the city link in a notice dialog closes the dialog and opens the city", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const item = shared.items.lostFull
    const dialog = new RouteDialog(page, t)
    await go(`/lost?search=${shared.search.view}`)
    await new ItemCard(page, t).byTitle(item.title).click()
    await dialog.expectOpen()

    await dialog.root
      .getByRole("link", { name: t("item.cityLink", { in: t("city.lviv.in") }) })
      .click()

    await expect(page).toHaveURL(/\/lviv$/)
    await expect(dialog.root).toBeHidden()
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

  test("choosing a city under a guide's map opens it at where to turn there", async ({
    page,
    go,
    t,
  }) => {
    await go("/guide/documents")
    await page.getByRole("combobox", { name: t("home.citiesPick") }).click()
    await page.getByRole("option", { name: t("city.odesa.name"), exact: true }).click()

    await expect(page).toHaveURL(/\/odesa#lost-property$/)
    const section = page.locator("#lost-property")
    await expect(section).toBeFocused()
    await expect(section).toBeInViewport()
  })
})
