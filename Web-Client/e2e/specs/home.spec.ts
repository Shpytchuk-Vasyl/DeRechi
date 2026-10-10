import type { Page } from "@playwright/test"
import { expect, test } from "../fixtures/base"
import { Filters, ReportForm, RouteDialog } from "../fixtures/pages"
import {
  BENEFIT_NUMBERS,
  type BenefitNumber,
  FAQ_NUMBERS,
  HOME_CHIPS,
  HomePage,
  RAIL_SIZE,
} from "../fixtures/pages/home-page"
import type { ItemKind } from "../support/data"
import { env } from "../support/env"
import { readJsonLd } from "../support/seo"
import { setCountry } from "../support/site"

const KINDS: ItemKind[] = ["found", "lost"]

function reportTitleKey(kind: ItemKind) {
  return kind === "lost" ? "form.pageTitleLost" : "form.pageTitleFound"
}

function homeUrl(locale: string): RegExp {
  return new RegExp(`/${locale}/?$`)
}

test.describe("hero report buttons", () => {
  for (const kind of KINDS) {
    test(`the ${kind} button opens the report form as a modal, Escape goes back home`, async ({
      page,
      go,
      t,
      appLocale,
    }) => {
      await go("/")
      const home = new HomePage(page, t)
      const button = home.heroReportLink(kind)
      await expect(button).toHaveAttribute("href", `/${appLocale}/report/${kind}`)

      await button.click()

      await expect(page).toHaveURL(new RegExp(`/${appLocale}/report/${kind}$`))
      const dialog = new RouteDialog(
        page,
        t,
        page.getByRole("dialog", { name: t(reportTitleKey(kind)), exact: true }),
      )
      await dialog.expectOpen()
      await expect(new ReportForm(page, t, kind, { modal: true }).title()).toBeVisible()

      await dialog.close("escape")

      await expect(page).toHaveURL(homeUrl(appLocale))
      await expect(home.heroHeading()).toBeVisible()
    })
  }
})

test.describe("hero search", () => {
  test("a query goes to the found list with ?search=", async ({
    page,
    go,
    t,
    appLocale,
    shared,
  }) => {
    await go("/")
    const box = new HomePage(page, t).searchBox()
    const query = `${shared.search.view} ${t("category.KEYS")}`

    await box.fill(query)
    await box.press("Enter")

    await expect(page).toHaveURL(new RegExp(`/${appLocale}/found\\?search=`))
    expect(new URL(page.url()).searchParams.get("search")).toBe(query)
    await expect(new Filters(page, t).searchBox()).toHaveValue(query)
  })

  test("an empty or blank query goes to the lost list", async ({ page, go, t, appLocale }) => {
    for (const [name, value] of [
      ["empty", ""],
      ["blank", "   "],
    ]) {
      await test.step(name, async () => {
        await go("/")
        const box = new HomePage(page, t).searchBox()

        await box.fill(value)
        await box.press("Enter")

        await expect(page).toHaveURL(new RegExp(`/${appLocale}/lost$`))
      })
    }
  })
})

test.describe("category chips", () => {
  test("each chip opens the found list with its category selected in the filter", async ({
    page,
    go,
    t,
    appLocale,
  }) => {
    const home = new HomePage(page, t)
    const filters = new Filters(page, t)

    for (const key of HOME_CHIPS) {
      await test.step(key, async () => {
        await go("/")
        const chip = home.chip(key)
        await expect(chip).toHaveAttribute("href", `/${appLocale}/found?category=${key}`)

        await chip.click()

        await expect(page).toHaveURL(new RegExp(`/${appLocale}/found\\?category=${key}$`))
        await expect(filters.categorySelect()).toHaveText(t(`category.${key}`))
      })
    }
  })
})

test.describe("rails", () => {
  for (const kind of KINDS) {
    test(`the ${kind} rail has a heading, at most ${RAIL_SIZE} cards to /${kind}/<id> and "See all"`, {
      tag: "@responsive",
    }, async ({ page, go, t, appLocale }) => {
      await go("/")
      const home = new HomePage(page, t)

      await expect(home.railHeading(kind)).toBeVisible()
      await expect(home.railError(kind)).toHaveCount(0)

      const cards = home.railCards(kind)
      const count = await cards.count()
      expect(count).toBeLessThanOrEqual(RAIL_SIZE)
      await expect(cards.filter({ visible: true })).toHaveCount(
        Math.min(count, home.railVisibleMax()),
      )
      const hrefs = await cards.evaluateAll((links) =>
        links.map((link) => link.getAttribute("href")),
      )
      for (const href of hrefs) expect(href).toMatch(new RegExp(`^/${appLocale}/${kind}/\\d+$`))

      const seeAll = home.seeAllLink(kind)
      await expect(seeAll).toHaveAttribute("href", `/${appLocale}/${kind}`)
      await seeAll.click()

      await expect(page).toHaveURL(new RegExp(`/${appLocale}/${kind}$`))
    })
  }
})

test.describe("FAQ", () => {
  test("one answer is open at a time and a second click closes it", async ({ page, go, t }) => {
    await go("/")
    const home = new HomePage(page, t)
    const [first, second] = [home.faqQuestion(1), home.faqQuestion(2)]
    await expect(home.faqHeading()).toBeVisible()
    await expect(home.faqExpanded()).toHaveCount(0)

    await first.click()
    await expect(first).toHaveAttribute("aria-expanded", "true")
    await expect(home.faqAnswer(1)).toBeVisible()

    await second.click()
    await expect(second).toHaveAttribute("aria-expanded", "true")
    await expect(first).toHaveAttribute("aria-expanded", "false")
    await expect(home.faqAnswer(1)).toBeHidden()
    await expect(home.faqAnswer(2)).toBeVisible()
    await expect(home.faqExpanded()).toHaveCount(1)

    await second.click()
    await expect(second).toHaveAttribute("aria-expanded", "false")
    await expect(home.faqExpanded()).toHaveCount(0)
  })

  test("the finders-law answer (Q4) follows the country cookie: UA and PL differ", async ({
    page,
    context,
    go,
    t,
  }) => {
    const home = new HomePage(page, t)

    const read = async (n: 1 | 4): Promise<string> => {
      await home.faqQuestion(n).click()
      await expect(home.faqAnswer(n)).toBeVisible()
      return (await home.faqAnswer(n).innerText()).trim()
    }

    const answers: Record<string, { law: string; other: string }> = {}
    for (const country of ["UA", "PL"]) {
      await test.step(country, async () => {
        await setCountry(context, country)
        await go("/")
        const other = await read(1)
        const law = await read(4)
        answers[country] = { law, other }
      })
    }

    expect(answers.UA.law).not.toBe("")
    expect(answers.PL.law).not.toBe("")
    expect(answers.PL.law).not.toBe(answers.UA.law)
    expect(answers.PL.other).toBe(answers.UA.other)
  })
})

test.describe("benefit tabs", () => {
  const panelOf = (home: HomePage, n: BenefitNumber) =>
    home.benefits().getByRole("tabpanel", { name: home.benefitTitle(n), exact: true })

  async function expectSelected(home: HomePage, n: BenefitNumber): Promise<void> {
    for (const other of BENEFIT_NUMBERS) {
      await expect(home.tab(other)).toHaveAttribute("aria-selected", String(other === n))
    }
  }

  test("a click selects the tab and swaps the panel", async ({ page, go, t }) => {
    await go("/")
    const home = new HomePage(page, t)
    await expect(home.tabs()).toHaveCount(BENEFIT_NUMBERS.length)
    await expectSelected(home, 1)
    await expect(panelOf(home, 1)).toBeVisible()

    for (const n of [3, 5, 2] as const) {
      await home.tab(n).click()
      await expectSelected(home, n)
      await expect(panelOf(home, n)).toBeVisible()
      await expect(home.tabPanel()).toHaveCount(1)
    }
  })
})

test.describe("mascot", () => {
  async function pauseClock(page: Page): Promise<void> {
    const now = await page.evaluate(() => Date.now())
    await page.clock.pauseAt(now + 60_000)
  }

  test("with reduced motion there is no mascot to catch", async ({ page, go, t }) => {
    await page.clock.install()
    await go("/")
    const home = new HomePage(page, t)
    await expect(home.heroHeading()).toBeVisible()
    await pauseClock(page)

    await page.clock.runFor(20_000)

    await expect(home.mascot()).toHaveCount(0)
  })
})

test.describe("JSON-LD", () => {
  test("WebSite with a SearchAction and FAQPage, every block valid JSON", async ({
    page,
    go,
    t,
    appLocale,
  }) => {
    await go("/")
    const blocks = await readJsonLd(page)
    const home = `${env.siteURL.replace(/\/$/, "")}/${appLocale}`

    const website = blocks.find((block) => block["@type"] === "WebSite")
    expect(website).toMatchObject({
      "@context": "https://schema.org",
      "@type": "WebSite",
      name: t("app.name"),
      url: home,
      potentialAction: {
        "@type": "SearchAction",
        target: {
          "@type": "EntryPoint",
          urlTemplate: `${home}/found?search={search_term_string}`,
        },
        "query-input": "required name=search_term_string",
      },
    })

    const faq = blocks.find((block) => block["@type"] === "FAQPage") as
      | { mainEntity?: { name?: string }[] }
      | undefined
    expect(faq?.mainEntity?.map((question) => question.name)).toEqual(
      FAQ_NUMBERS.map((n) => t(`home.faqQ${n}`)),
    )
  })
})
