import type { Locator, Page } from "@playwright/test"
import { expect, test } from "../fixtures/base"
import { expectListQuery } from "../fixtures/pages/list-page"
import { roundedCoordinate } from "../support/data"
import type { Translator } from "../support/i18n"
import { GEO_LATITUDE_HEADER, GEO_LONGITUDE_HEADER } from "../support/site"

const WEEK_MS = 7 * 24 * 60 * 60_000

const APPEARS = { timeout: 10_000 }

function hint(page: Page, t: Translator): Locator {
  return page.getByRole("complementary", { name: t("list.cityHint.label"), exact: true })
}

async function scrollDown(page: Page): Promise<void> {
  await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toBeVisible()
  await page.evaluate(() => {
    const vh = window.innerHeight
    const tops = Array.from(
      document.querySelectorAll("main a:has(h3)"),
      (card) => card.getBoundingClientRect().top + window.scrollY,
    )
    const top = tops.find((it) => it - vh * 0.7 >= 300)
    window.scrollTo(0, top === undefined ? 600 : top - vh * 0.7)
  })
}

test.describe("Znaida's hint on the lists", () => {
  test("with a city from the IP address it offers the city's list after a scroll", async ({
    page,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const here = shared.place
    await page.setExtraHTTPHeaders({
      [GEO_LATITUDE_HEADER]: String(here.lat),
      [GEO_LONGITUDE_HEADER]: String(here.lon),
    })
    const where = t("city.lviv.in")

    await go("/found")
    await expect(hint(page, t)).toHaveCount(0)
    await scrollDown(page)

    await expect(hint(page, t).getByText(t("list.cityHint.titleCity", { in: where }))).toBeVisible(
      APPEARS,
    )
    await hint(page, t)
      .getByRole("link", { name: t("list.cityHint.goFound", { in: where }), exact: true })
      .click()
    await expectListQuery(page, `/${appLocale}/lviv/found`, {})
  })

  test("without a city it offers Near me, which puts your location in the list's address", async ({
    page,
    context,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const here = shared.place
    await context.grantPermissions(["geolocation"])
    await context.setGeolocation({ latitude: here.lat, longitude: here.lon })

    await go("/lost")
    await scrollDown(page)

    await expect(hint(page, t).getByText(t("list.cityHint.titleNear"))).toBeVisible(APPEARS)
    await expect(hint(page, t).getByRole("link")).toHaveCount(0)
    await hint(page, t)
      .getByRole("button", { name: t("list.nearby"), exact: true })
      .click()

    await expectListQuery(page, `/${appLocale}/lost`, {
      lat: roundedCoordinate(here.lat),
      lon: roundedCoordinate(here.lon),
    })
    await expect(hint(page, t)).toHaveCount(0)
  })

  test("a closed hint rests for a week", async ({ page, go, t }) => {
    await go("/found")
    await scrollDown(page)
    await expect(hint(page, t)).toBeVisible(APPEARS)
    await hint(page, t)
      .getByRole("button", { name: t("common.close"), exact: true })
      .click()
    await expect(hint(page, t)).toHaveCount(0)

    await page.reload()
    await scrollDown(page)
    await expect(hint(page, t)).toHaveCount(0)

    await page.evaluate(
      (closedAt) => localStorage.setItem("derechi.city-hint", String(closedAt)),
      Date.now() - WEEK_MS - 60_000,
    )
    await page.reload()
    await scrollDown(page)
    await expect(hint(page, t)).toBeVisible(APPEARS)
  })

  test("there is no hint on a city's list or on a list already near you", async ({
    page,
    go,
    t,
    shared,
  }) => {
    for (const path of ["/lviv/found", `/found?lat=${shared.place.lat}&lon=${shared.place.lon}`]) {
      await go(path)
      await scrollDown(page)
      await expect(hint(page, t), path).toHaveCount(0)
    }
  })
})
