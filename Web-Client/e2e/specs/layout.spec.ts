import { expect, test } from "../fixtures/base"
import { ItemCard, ItemDetail, RouteDialog } from "../fixtures/pages"
import { walkKeyPages } from "../support/key-pages"
import { expectNoHorizontalScroll } from "../support/viewport"

test.describe("no horizontal scroll", () => {
  test("home, a list, details, every report step, safety, terms", {
    tag: "@responsive",
  }, async ({ page, t, go, shared }) => {
    test.setTimeout(120_000)
    await walkKeyPages({ page, t, go, shared }, (label) => expectNoHorizontalScroll(page, label))
  })
})

const SWEEP_WIDTHS = [360, 390, 412, 767, 768, 820, 900, 901, 1024, 1280, 1920]

test.describe("width sweep", { tag: "@layout-sweep" }, () => {
  test("every width: no horizontal scroll and exactly one visible menu navigation", async ({
    page,
    t,
    go,
    shared,
  }) => {
    test.setTimeout(300_000)
    const height = page.viewportSize()?.height ?? 900
    const menus = page
      .getByRole("navigation", { name: t("nav.menu"), exact: true })
      .filter({ visible: true })

    await walkKeyPages({ page, t, go, shared }, async (label) => {
      for (const width of SWEEP_WIDTHS) {
        await page.setViewportSize({ width, height })
        await expectNoHorizontalScroll(page, `${label} @ ${width}px`)
        await expect.soft(menus, `one visible nav.menu: ${label} @ ${width}px`).toHaveCount(1)
      }
      await page.setViewportSize({ width: 1440, height })
    })
  })
})

test.describe("item modal", () => {
  test("a bottom sheet up to 900 px, centred above; the close button and the overlay close it", {
    tag: "@responsive",
  }, async ({ page, go, t, shared, appLocale }) => {
    const item = shared.items.lostFull
    const search = shared.search.view
    const dialog = new RouteDialog(page, t)
    const cards = new ItemCard(page, t)
    const listUrl = new RegExp(`/${appLocale}/lost\\?search=${search}$`)

    await go(`/lost?search=${search}`)

    for (const by of ["button", "overlay"] as const) {
      await test.step(by, async () => {
        await cards.byTitle(item.title).click()
        await expect(page).toHaveURL(new RegExp(`/${appLocale}/lost/${item.id}$`))
        await dialog.expectOpen()
        await expect(
          new ItemDetail(page, t, "lost", { modal: true }).heading(item.title),
        ).toBeVisible()
        await dialog.expectShape()

        await dialog.close(by)
        await expect(page).toHaveURL(listUrl)
        await expect(cards.byTitle(item.title)).toBeVisible()
      })
    }
  })
})
