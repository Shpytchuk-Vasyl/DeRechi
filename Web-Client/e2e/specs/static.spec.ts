import type { Page } from "@playwright/test"
import { expect, test } from "../fixtures/base"
import * as api from "../support/api"
import type { Locale } from "../support/data"
import { DISTINCT_SLOT, jurisdictionOf, legalSlotText } from "../support/legal"
import {
  allowDocumentStatus,
  COUNTRY_COOKIE,
  DEFAULT_LOCALE,
  GEO_COUNTRY_HEADER,
  pathnameOf,
  setCountry,
} from "../support/site"

const UNKNOWN_LANGUAGE = "ja"

test.describe("/ in an unknown language", () => {
  test("Accept-Language the site does not have falls back to /en", async ({ request }) => {
    const response = await request.get("/", {
      maxRedirects: 0,
      headers: { "accept-language": UNKNOWN_LANGUAGE },
    })
    expect(response.status()).toBe(307)
    expect(pathnameOf(response.headers().location)).toBe(`/${DEFAULT_LOCALE}`)
  })
})

test.describe("unknown locale prefix", () => {
  test("/xx/lost in the browser is the localized 404 of the browser's language", async ({
    page,
    t,
    appLocale,
    consoleErrors,
  }) => {
    allowDocumentStatus(consoleErrors, 404)
    const response = await page.goto("/xx/lost")

    expect(response?.status()).toBe(404)
    await expect(page).toHaveURL(new RegExp(`/${appLocale}/xx/lost$`))
    await expect(page.locator("html")).toHaveAttribute("lang", appLocale)
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(t("error.notFoundTitle"))
  })
})

async function expectJurisdiction(
  page: Page,
  locale: Locale,
  doc: "terms" | "privacy",
  country: string,
): Promise<void> {
  const main = page.getByRole("main")
  await expect(main.getByRole("heading", { level: 1 })).toBeVisible()
  await expect(main).toContainText(legalSlotText(country, locale, DISTINCT_SLOT[doc]))
}

test.describe("legal pages and the country", () => {
  test("?country=PL switches terms and privacy to Polish law", async ({ page, go, appLocale }) => {
    for (const doc of ["terms", "privacy"] as const) {
      await go(`/${doc}?country=PL`)
      await expectJurisdiction(page, appLocale, doc, "PL")
    }
  })

  test("the DERECHI_COUNTRY cookie picks the jurisdiction, ?country= beats it", async ({
    page,
    go,
    context,
    appLocale,
  }) => {
    await setCountry(context, "FR")

    await go("/terms")
    await expectJurisdiction(page, appLocale, "terms", "FR")
    await go("/privacy")
    await expectJurisdiction(page, appLocale, "privacy", "FR")

    await go("/terms?country=PL")
    await expectJurisdiction(page, appLocale, "terms", "PL")
  })

  test("the x-vercel-ip-country header picks the jurisdiction and sets the cookie", async ({
    page,
    go,
    context,
    appLocale,
  }) => {
    await page.setExtraHTTPHeaders({ [GEO_COUNTRY_HEADER]: "DE" })

    await go("/terms")
    await expectJurisdiction(page, appLocale, "terms", "DE")

    const cookie = (await context.cookies()).find((it) => it.name === COUNTRY_COOKIE)
    expect(cookie?.value).toBe("DE")
  })

  test("an unsupported country falls back to the first one of the API", async ({
    page,
    go,
    appLocale,
  }) => {
    const supported = (await api.countries()).map((country) => country.code)
    const first = supported[0]
    if (!first) throw new Error("e2e: Client-API reports no countries")
    expect(supported, "Client-API supports US, so it is no unsupported country").not.toContain("US")
    for (const country of ["US", "zz9"]) {
      await go(`/terms?country=${country}`)
      await expectJurisdiction(page, appLocale, "terms", jurisdictionOf(first))
    }
  })
})

test.describe("404 page", () => {
  test("/nope: heading, links home and to the found list work", async ({
    page,
    go,
    t,
    appLocale,
    consoleErrors,
  }) => {
    allowDocumentStatus(consoleErrors, 404)
    const main = page.getByRole("main")
    const home = main.getByRole("link", { name: t("error.home"), exact: true })
    const found = main.getByRole("link", { name: t("error.browseFound"), exact: true })

    const response = await go("/nope")
    expect(response?.status()).toBe(404)
    await expect(main.getByRole("heading", { level: 1 })).toHaveText(t("error.notFoundTitle"))
    await expect(home).toHaveAttribute("href", `/${appLocale}`)
    await expect(found).toHaveAttribute("href", `/${appLocale}/found`)

    await home.click()
    await expect(page).toHaveURL(new RegExp(`/${appLocale}/?$`))

    await go("/nope")
    await found.click()
    await expect(page).toHaveURL(new RegExp(`/${appLocale}/found$`))
    await expect(main.getByRole("heading", { level: 1 })).toHaveText(t("list.foundTitle"))
  })
})

test.describe("an item that does not exist", () => {
  for (const kind of ["lost", "found"] as const) {
    test(`/${kind}/999999999999 is a 404`, async ({ page, go, t, consoleErrors }) => {
      allowDocumentStatus(consoleErrors, 404)
      const response = await go(`/${kind}/999999999999`)

      expect(response?.status()).toBe(404)
      await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toHaveText(
        t("error.notFoundTitle"),
      )
    })
  }

  test.fixme("/lost/abc is a 404, not the error page", async ({ page, go, t, consoleErrors }) => {
    allowDocumentStatus(consoleErrors, 404)
    const response = await go("/lost/abc")

    expect(response?.status()).toBe(404)
    await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toHaveText(
      t("error.notFoundTitle"),
    )
  })
})
