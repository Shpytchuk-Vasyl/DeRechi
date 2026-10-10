import { expect, test } from "../fixtures/base"
import { Filters, ItemDetail } from "../fixtures/pages"
import { expectListQuery, holdServerActions, ListPage } from "../fixtures/pages/list-page"
import * as api from "../support/api"
import type { SharedGroup } from "../support/shared"

const PAGE_SIZE = 15

test.describe("search and counter", () => {
  const groups: SharedGroup[] = ["cnt1x", "cnt3x", "cnt5x"]

  test("searching each counter group through the filter finds it whole, counted in the plural", async ({
    page,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const filters = new Filters(page, t)

    for (const group of groups) {
      await test.step(group, async () => {
        const { kind } = shared.groups[group][0]
        const search = shared.search[group]
        const { items } = await api.items(kind, { search })
        expect(items).toHaveLength(shared.groups[group].length)
        const list = new ListPage(page, t, kind)

        await go(list.path())
        await filters.search(search)
        await filters.apply()

        await expectListQuery(page, `/${appLocale}/${kind}`, { search })
        await expect(filters.searchBox()).toHaveValue(search)
        await expect(list.cards.counter(items.length)).toBeVisible()
        await expect(list.cards.all()).toHaveCount(items.length)
        await expect(list.cards.showMoreButton()).toHaveCount(0)

        for (const item of items) {
          const card = list.cards.byTitle(item.title)
          await expect(card).toBeVisible()
          await expect(card).toHaveAttribute(
            "href",
            new RegExp(`/${appLocale}/${kind}/${item.id}$`),
          )
          await expect(card.getByText(item.place.name, { exact: true })).toBeVisible()
          await expect(list.cards.date(card)).toHaveAttribute("datetime", item.date)
        }
      })
    }
  })
})

test.describe("pagination", () => {
  test("15 cards, Show more loads the rest while aria-busy, then disappears", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const count = shared.groups.page.length
    expect(count).toBeGreaterThan(PAGE_SIZE)
    const list = new ListPage(page, t, "lost")

    await go(list.path({ search: shared.search.page }))
    await expect(list.cards.all()).toHaveCount(PAGE_SIZE)
    await expect(list.cards.counter(PAGE_SIZE)).toBeVisible()

    const actions = await holdServerActions(page)
    const more = list.cards.showMoreButton()
    try {
      await more.click()
      await actions.held
      await expect(more).toHaveAttribute("aria-busy", "true")
      await expect(more).toBeDisabled()
    } finally {
      await actions.release()
    }

    await expect(list.cards.all()).toHaveCount(count)
    await expect(more).toHaveCount(0)
    await expect(list.cards.counter(PAGE_SIZE)).toBeVisible()
  })

  test.fixme("the counter follows the cards after Show more", async ({ page, go, t, shared }) => {
    const count = shared.groups.page.length
    const list = new ListPage(page, t, "lost")

    await go(list.path({ search: shared.search.page }))
    await list.cards.showMoreButton().click()

    await expect(list.cards.all()).toHaveCount(count)
    await expect(list.cards.counter(count)).toBeVisible()
  })

  test("?after=<cursor> opened directly shows the next page", async ({ page, go, t, shared }) => {
    const search = shared.search.page
    const count = shared.groups.page.length
    const first = await api.items("lost", { search }, { first: PAGE_SIZE })
    if (!first.endCursor) throw new Error("the API returned no cursor for the first page")
    const rest = await api.items("lost", { search }, { after: first.endCursor })
    expect(rest.items).toHaveLength(count - PAGE_SIZE)

    const list = new ListPage(page, t, "lost")
    await go(list.path({ search, after: first.endCursor }))

    await expect(list.cards.all()).toHaveCount(count - PAGE_SIZE)
    await expect(list.cards.counter(count - PAGE_SIZE)).toBeVisible()
    await expect(list.cards.showMoreButton()).toHaveCount(0)
    for (const item of rest.items) await expect(list.cards.byTitle(item.title)).toBeVisible()
  })
})

test.describe("empty result", () => {
  for (const kind of ["lost", "found"] as const) {
    test(`a ${kind} search without matches says so and links to the unfiltered list`, async ({
      page,
      go,
      t,
      shared,
      appLocale,
    }) => {
      const list = new ListPage(page, t, kind)
      await go(list.path({ search: `${shared.marker}-none` }))

      await expect(list.emptyTitle()).toBeVisible()
      await expect(list.emptyHint()).toBeVisible()
      await expect(list.cards.all()).toHaveCount(0)

      const reset = list.emptyReset()
      await expect(reset).toHaveAttribute("href", `/${appLocale}/${kind}`)
      await reset.click()
      await expectListQuery(page, `/${appLocale}/${kind}`, {})
    })
  }
})

test.describe("item modal from a card", () => {
  test("soft click opens the modal; Escape and history keep every filter of the query", async ({
    page,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const items = shared.groups.sort
    const [item] = items
    const search = shared.search.sort
    const list = new ListPage(page, t, "lost")
    const query = { search, category: "WALLET", sort: "TITLE_ASC" }
    const listPath = `/${appLocale}/lost`
    const itemPath = `/${appLocale}/lost/${item.id}`

    await go(list.path(query))
    await expect(list.cards.all()).toHaveCount(items.length)

    const modal = new ItemDetail(page, t, "lost", { modal: true })
    const opened = async () => {
      await expect(modal.root()).toBeVisible()
      await expect(modal.root()).toHaveAttribute("data-state", "open")
      await expect(modal.heading(item.title)).toBeVisible()
    }

    await list.cards.byTitle(item.title).click()
    await expectListQuery(page, itemPath, {})
    await opened()
    await expect(modal.breadcrumb()).toHaveCount(0)

    await page.keyboard.press("Escape")
    await expectListQuery(page, listPath, query)
    await expect(modal.root()).toHaveCount(0)
    await expect(new Filters(page, t).searchBox()).toHaveValue(search)
    await expect(list.cards.all()).toHaveCount(items.length)

    await page.goForward()
    await expectListQuery(page, itemPath, {})
    await opened()

    await page.goBack()
    await expectListQuery(page, listPath, query)
    await expect(modal.root()).toHaveCount(0)
    await expect(list.cards.all()).toHaveCount(items.length)
  })
})
