import { expect, test } from "../fixtures/base"
import { Filters } from "../fixtures/pages"
import { expectListQuery, ListPage } from "../fixtures/pages/list-page"
import type { ItemSort } from "../support/api"
import { kyivDate, shiftDay } from "../support/data"
import type { SharedData, SharedGroup, SharedItem } from "../support/shared"

function member(shared: SharedData, group: SharedGroup, key: string): SharedItem {
  const item = shared.groups[group].find((it) => it.key === key)
  if (!item) throw new Error(`e2e: the shared group ${group} has no item ${key}`)
  return item
}

test.describe("category", () => {
  test("a category keeps one of two items and shows as selected", {
    tag: "@responsive",
  }, async ({ page, go, t, shared, appLocale }) => {
    const search = shared.search.cat
    const wallet = member(shared, "cat", "wallet")
    const keys = member(shared, "cat", "keys")
    const list = new ListPage(page, t, "lost")
    const filters = new Filters(page, t)

    await go(list.path({ search }))
    await expect(list.cards.all()).toHaveCount(2)

    await filters.category(keys.category)
    await filters.apply()

    await expectListQuery(page, `/${appLocale}/lost`, { search, category: keys.category })
    await expect(list.cards.byTitle(keys.title)).toBeVisible()
    await expect(list.cards.byTitle(wallet.title)).toHaveCount(0)
    await expect(list.cards.all()).toHaveCount(1)

    await filters.openSheet()
    await expect(filters.categorySelect()).toHaveText(t(`category.${keys.category}`))
  })

  test("below 768 px the Filters button counts the active filters", {
    tag: "@mobile-only",
  }, async ({ page, go, t, shared }) => {
    const filters = new Filters(page, t)

    await go(new ListPage(page, t, "lost").path({ search: shared.search.cat }))
    await expect(filters.activeCount()).toHaveText("1")

    await go(new ListPage(page, t, "lost").path({ search: shared.search.cat, category: "KEYS" }))
    await expect(filters.activeCount()).toHaveText("2")
  })
})

test.describe("date range", () => {
  test("future days are disabled, a range keeps the item inside it, the picker clears it", {
    tag: "@responsive",
  }, async ({ page, go, t, shared, appLocale }) => {
    const search = shared.search.date
    const items = shared.groups.date
    const inside = member(shared, "date", "d10")
    const from = shiftDay(inside.input.date, -2)
    const to = shiftDay(inside.input.date, 2)
    for (const other of items.filter((it) => it !== inside)) {
      expect(other.input.date < from || other.input.date > to, `${other.title} is outside`).toBe(
        true,
      )
    }
    const list = new ListPage(page, t, "lost")
    const filters = new Filters(page, t)
    const { calendar } = filters

    await go(list.path({ search }))
    await expect(list.cards.all()).toHaveCount(items.length)

    await filters.openDates()
    await expect(calendar.dayButton(kyivDate(0))).toBeEnabled()
    await calendar.showMonthOf(kyivDate(1))
    await expect(calendar.dayButton(kyivDate(1))).toBeDisabled()
    await calendar.pick(from)
    await calendar.pick(to)
    await filters.closeDates()
    await expect(filters.datesTrigger()).not.toHaveText(t("list.anyDate"))
    await filters.apply()

    await expectListQuery(page, `/${appLocale}/lost`, { search, dateFrom: from, dateTo: to })
    await expect(list.cards.all()).toHaveCount(1)
    await expect(list.cards.byTitle(inside.title)).toBeVisible()

    await filters.openSheet()
    await filters.datesClearButton().click()
    await expect(filters.datesTrigger()).toHaveText(t("list.anyDate"))
    await filters.apply()

    await expectListQuery(page, `/${appLocale}/lost`, { search })
    await expect(list.cards.all()).toHaveCount(items.length)
  })
})

test.describe("sort", () => {
  test("each of the four orders sorts the group; the default one leaves the URL", {
    tag: "@responsive",
  }, async ({ page, go, t, shared, appLocale }) => {
    const search = shared.search.sort
    const items = shared.groups.sort
    const titles = (compare: (a: SharedItem, b: SharedItem) => number) =>
      [...items].sort(compare).map((it) => it.title)
    const newest = titles((a, b) => b.input.date.localeCompare(a.input.date))
    const byTitle = titles((a, b) => a.title.localeCompare(b.title))
    const list = new ListPage(page, t, "lost")
    const filters = new Filters(page, t)
    const path = `/${appLocale}/lost`

    await go(list.path({ search }))
    await expect(list.titles()).toHaveText(newest)

    const orders: { sort: ItemSort; titles: string[] }[] = [
      { sort: "DATE_ASC", titles: [...newest].reverse() },
      { sort: "TITLE_ASC", titles: byTitle },
      { sort: "TITLE_DESC", titles: [...byTitle].reverse() },
      { sort: "DATE_DESC", titles: newest },
    ]
    for (const { sort, titles } of orders) {
      await filters.sort(sort)
      await expectListQuery(page, path, sort === "DATE_DESC" ? { search } : { search, sort })
      await expect(list.titles()).toHaveText(titles)
    }
  })
})

test.describe("reset", () => {
  test("Reset filters clears the whole query, coordinates included", {
    tag: "@responsive",
  }, async ({ page, go, t, shared, appLocale }) => {
    const wallet = member(shared, "cat", "wallet")
    const { lat, lon } = wallet.input.place
    const list = new ListPage(page, t, "lost")
    const filters = new Filters(page, t)

    await go(
      list.path({
        search: shared.search.cat,
        category: wallet.category,
        dateFrom: wallet.input.date,
        dateTo: wallet.input.date,
        sort: "TITLE_ASC",
        lat: String(lat),
        lon: String(lon),
      }),
    )
    await expect(list.cards.all()).toHaveCount(1)

    await filters.reset()

    await expectListQuery(page, `/${appLocale}/lost`, {})
    await expect(filters.searchBox()).toHaveValue("")
  })

  test("the Apply hint shows while the draft differs from the URL", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const search = shared.search.cat
    await go(new ListPage(page, t, "lost").path({ search }))
    const filters = new Filters(page, t)
    await expect(filters.applyHint()).toBeHidden()

    await filters.search(`${search}x`)
    await expect(filters.applyHint()).toBeVisible()

    await filters.search(search)
    await expect(filters.applyHint()).toBeHidden()

    await filters.category("KEYS")
    await expect(filters.applyHint()).toBeVisible()

    await filters.category(null)
    await expect(filters.applyHint()).toBeHidden()
  })
})

test.describe("near me", () => {
  test("in Lviv the switch keeps the Lviv item, not the Kyiv one, and switching off restores both", async ({
    page,
    context,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const search = shared.search.near
    const lviv = member(shared, "near", "lviv")
    const kyiv = member(shared, "near", "kyiv")
    const here = lviv.input.place
    await context.grantPermissions(["geolocation"])
    await context.setGeolocation({ latitude: here.lat, longitude: here.lon })
    const list = new ListPage(page, t, "found")
    const path = `/${appLocale}/found`

    await go(list.path({ search }))
    await expect(list.cards.all()).toHaveCount(2)

    const nearby = list.nearbySwitch()
    await expect(nearby).not.toBeChecked()
    await nearby.click()

    const round = (value: number) => String(Math.round(value * 1000) / 1000)
    await expectListQuery(page, path, { search, lat: round(here.lat), lon: round(here.lon) })
    await expect(nearby).toBeChecked()
    await expect(list.cards.byTitle(lviv.title)).toBeVisible()
    await expect(list.cards.byTitle(kyiv.title)).toHaveCount(0)

    await nearby.click()
    await expectListQuery(page, path, { search })
    await expect(nearby).not.toBeChecked()
    await expect(list.cards.all()).toHaveCount(2)
  })

  test("a refused location leaves the list and explains why in a status note", async ({
    page,
    context,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const search = shared.search.near
    await context.clearPermissions()
    const list = new ListPage(page, t, "found")

    await go(list.path({ search }))
    await list.nearbySwitch().click()

    await expect(list.nearbyProblem("list.nearbyDenied")).toBeVisible()
    await expect(list.nearbySwitch()).not.toBeChecked()
    await expectListQuery(page, `/${appLocale}/found`, { search })
  })

  test("lost lists have no Near me switch", async ({ page, go, t, shared }) => {
    const list = new ListPage(page, t, "lost")

    await go(list.path({ search: shared.search.cnt1x }))
    await expect(list.cards.all()).toHaveCount(shared.groups.cnt1x.length)
    await expect(list.nearbySwitch()).toHaveCount(0)
  })
})
