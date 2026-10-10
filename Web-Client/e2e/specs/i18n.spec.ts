import { expect, test } from "../fixtures/base"
import { ItemCard, ItemDetail, Shell } from "../fixtures/pages"
import { browserCountryName, browserMoney, browserNoticeDate } from "../support/browser-intl"
import { type ItemKind, LOCALES, type Locale } from "../support/data"
import { waitForHydration } from "../support/hydration"
import { type Translator, translator, untranslated } from "../support/i18n"
import { walkKeyPages } from "../support/key-pages"
import { expectAlternates } from "../support/seo"
import type { SharedGroup, SharedItemKey } from "../support/shared"
import { allowDocumentStatus, LOCALE_COOKIE, otherLocale, pathnameOf } from "../support/site"
import { expectNoHorizontalScroll } from "../support/viewport"

type KeyPage = {
  path: string
  status: number
  title: (t: Translator, h1: string) => string
  alternates?: boolean
}

const KEY_PAGES: KeyPage[] = [
  { path: "/", status: 200, title: (t) => t("app.name") },
  {
    path: "/lost",
    status: 200,
    title: (t) => t("app.titleTemplate", { page: t("list.lostTitle") }),
  },
  {
    path: "/found",
    status: 200,
    title: (t) => t("app.titleTemplate", { page: t("list.foundTitle") }),
    alternates: true,
  },
  {
    path: "/report/lost",
    status: 200,
    title: (t) => t("app.titleTemplate", { page: t("form.pageTitleLost") }),
  },
  {
    path: "/safety",
    status: 200,
    title: (t) => t("app.titleTemplate", { page: t("safety.title") }),
  },
  { path: "/terms", status: 200, title: (t, h1) => t("app.titleTemplate", { page: h1 }) },
  { path: "/nope-e2e", status: 404, title: (t) => t("app.name") },
]

const COUNTERS: { group: SharedGroup; kind: ItemKind; count: number }[] = [
  { group: "cnt1x", kind: "lost", count: 1 },
  { group: "cnt3x", kind: "found", count: 3 },
  { group: "cnt5x", kind: "lost", count: 5 },
]

const SWITCH_LOCALES: readonly Locale[] = ["en", "de"]

const FORMAT_ITEMS: {
  key: SharedItemKey
  currency: string
  locales: readonly Locale[]
  alternates: boolean
}[] = [
  { key: "lostFull", currency: "UAH", locales: LOCALES, alternates: true },
  { key: "lostRewardPL", currency: "PLN", locales: ["pl"], alternates: false },
]

function switchFrom(locale: Locale): Locale {
  return locale === "uk" ? "en" : "uk"
}

for (const locale of LOCALES) {
  test.describe(locale, () => {
    test.use({ appLocale: locale, locale })

    if (SWITCH_LOCALES.includes(locale)) {
      test.describe("/ without a locale", () => {
        test("redirects by Accept-Language and by the DERECHI_LOCALE cookie", async ({
          request,
          page,
        }) => {
          const byHeader = await request.get("/", {
            maxRedirects: 0,
            headers: { "accept-language": `${locale},ja;q=0.5` },
          })
          expect(byHeader.status()).toBe(307)
          expect(pathnameOf(byHeader.headers().location)).toBe(`/${locale}`)

          const byCookie = await request.get("/", {
            maxRedirects: 0,
            headers: {
              "accept-language": otherLocale(locale),
              cookie: `${LOCALE_COOKIE}=${locale}`,
            },
          })
          expect(byCookie.status()).toBe(307)
          expect(pathnameOf(byCookie.headers().location)).toBe(`/${locale}`)

          await page.goto("/")
          await expect(page).toHaveURL(new RegExp(`/${locale}/?$`))
          await expect(page.locator("html")).toHaveAttribute("lang", locale)
        })
      })

      test.describe("language switcher", () => {
        test(`switches /found from ${switchFrom(locale)}: URL, html[lang], cookie and texts`, async ({
          page,
          t,
          context,
        }) => {
          const from = switchFrom(locale)
          await page.goto(`/${from}/found`)
          await waitForHydration(page)
          await expect(page.getByRole("heading", { level: 1 })).toHaveText(
            translator(from)("list.foundTitle"),
          )

          await new Shell(page, translator(from)).switchLocale(locale)

          await expect(page).toHaveURL(new RegExp(`/${locale}/found$`))
          await expect(page.locator("html")).toHaveAttribute("lang", locale)
          await expect
            .poll(
              async () => (await context.cookies()).find((it) => it.name === LOCALE_COOKIE)?.value,
            )
            .toBe(locale)
          await expect(page.getByRole("heading", { level: 1 })).toHaveText(t("list.foundTitle"))
          await expect(page).toHaveTitle(t("app.titleTemplate", { page: t("list.foundTitle") }))
          const shell = new Shell(page, t)
          await expect(shell.listLink("lost")).toBeVisible()
          await expect(shell.languageButton()).toBeVisible()
        })
      })
    }

    test.describe("key pages", () => {
      for (const key of KEY_PAGES) {
        test(`${key.path} answers ${key.status}, every text translated${key.alternates ? ", canonical and hreflang" : ""}`, async ({
          page,
          go,
          t,
          consoleErrors,
        }) => {
          if (key.status !== 200) allowDocumentStatus(consoleErrors, key.status)

          const response = await go(key.path)

          expect(response?.status()).toBe(key.status)
          await expect(page.locator("html")).toHaveAttribute("lang", locale)
          const h1 = page.getByRole("main").getByRole("heading", { level: 1 }).first()
          await expect(h1).toBeVisible()
          await expect(page).toHaveTitle(key.title(t, (await h1.innerText()).trim()))

          const text = await page.locator("body").innerText()
          expect(untranslated(text, locale)).toEqual([])

          if (key.alternates) await expectAlternates(page, locale, key.path)
        })
      }
    })

    if (locale !== "uk") {
      test.describe("plural counter", () => {
        test("1, 3 and 5 notices are counted in the language's plural", async ({
          page,
          go,
          t,
          shared,
        }) => {
          const cards = new ItemCard(page, t)
          for (const { group, kind, count } of COUNTERS) {
            await go(`/${kind}?search=${shared.search[group]}`)
            await expect(cards.all(), group).toHaveCount(count)
            await expect(cards.counter(count), group).toBeVisible()
          }
        })
      })
    }

    test.describe("item page formats", () => {
      for (const { key, currency, alternates } of FORMAT_ITEMS.filter((it) =>
        it.locales.includes(locale),
      )) {
        test(`${key}: date, country and reward in the language's format${alternates ? ", canonical and hreflang" : ""}`, async ({
          page,
          go,
          t,
          shared,
        }) => {
          const item = shared.items[key]
          const { date, place } = item.input
          const reward = item.input.compensation?.amount
          if (reward === undefined) throw new Error(`e2e: shared ${key} has no reward`)

          await go(item.path)
          const detail = new ItemDetail(page, t, item.kind)
          await expect(detail.heading(item.title)).toBeVisible()

          await expect(detail.date()).toHaveText(await browserNoticeDate(page, locale, date))
          const country = await browserCountryName(page, locale, place.countryCode)
          await expect(detail.place()).toContainText(`${place.name}, ${country}`)
          const amount = await browserMoney(page, locale, reward, currency)
          await expect(detail.reward(amount)).toBeVisible()

          if (alternates) await expectAlternates(page, locale, item.path)
        })
      }
    })

    if (locale !== "uk") {
      test.describe("long words on a phone", { tag: "@mobile-only" }, () => {
        test("no horizontal scroll and no untranslated text on the key pages", async ({
          page,
          t,
          go,
          shared,
        }) => {
          test.setTimeout(120_000)
          await walkKeyPages({ page, t, go, shared }, async (label) => {
            await expectNoHorizontalScroll(page, label)
            const text = await page.locator("body").innerText()
            expect.soft(untranslated(text, locale), `untranslated: ${label}`).toEqual([])
          })
        })
      })
    }
  })
}

test.describe("language switcher and the query", () => {
  test.fixme("keeps the query (filters) when switching the language", async ({ page, go, t }) => {
    await go("/found?search=x")
    await new Shell(page, t).switchLocale("en")
    await expect(page).toHaveURL(/\/en\/found\?search=x$/)
  })
})
