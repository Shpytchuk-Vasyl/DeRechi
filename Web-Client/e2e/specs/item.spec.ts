import { expect, test } from "../fixtures/base"
import { escapeRegExp, ItemCard, ItemPage } from "../fixtures/pages"
import { ListPage } from "../fixtures/pages/list-page"
import { browserCountryName, browserNoticeDate } from "../support/browser-intl"
import type { ItemKind } from "../support/data"
import { jsonLdScripts, readJsonLd } from "../support/seo"
import type { SharedItemKey } from "../support/shared"

test.describe("full page", () => {
  const pages: { kind: ItemKind; key: SharedItemKey }[] = [
    { kind: "lost", key: "lostFull" },
    { kind: "found", key: "foundPhoto" },
  ]

  for (const { kind, key } of pages) {
    test(`a ${kind} page shows title, category, date, place with country, map link and description`, async ({
      page,
      go,
      t,
      shared,
      appLocale,
    }) => {
      const item = shared.items[key]

      const response = await go(item.path)
      expect(response?.status()).toBe(200)
      await expect(page).toHaveTitle(new RegExp(escapeRegExp(item.title)))

      const detail = new ItemPage(page, t, kind)
      await expect(detail.heading(item.title)).toBeVisible()
      await expect(detail.heading(item.title)).toHaveAttribute("title", item.title)
      await expect(detail.categoryBadge(item.category)).toBeVisible()
      await expect(detail.date()).toHaveText(
        await browserNoticeDate(page, appLocale, item.input.date),
      )

      const country = await browserCountryName(page, appLocale, item.input.place.countryCode)
      await expect(detail.place()).toContainText(`${item.input.place.name}, ${country}`)

      const { lat, lon } = item.input.place
      const maps = detail.mapsLink()
      const href = new URL((await maps.getAttribute("href")) ?? "")
      expect(`${href.host}${href.pathname}`).toMatch(/^(www\.)?google\.com\/maps(\/|$)/)
      expect(decodeURIComponent(href.search)).toContain(`${lat},${lon}`)
      await expect(maps).toHaveAttribute("target", "_blank")
      await expect(maps).toHaveAttribute("rel", /noreferrer/)

      const description = item.input.description
      if (!description) throw new Error(`the shared ${key} has no description`)
      await expect(detail.descriptionHeading()).toBeVisible()
      await expect(detail.root().getByText(description, { exact: true })).toBeVisible()

      const breadcrumb = detail.breadcrumb()
      await expect(breadcrumb).toHaveAttribute("href", `/${appLocale}/${kind}`)
      await breadcrumb.click()
      await expect(page).toHaveURL(new RegExp(`/${appLocale}/${kind}$`))
    })
  }

  test("an item without a description says the author left none", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const item = shared.items.lostNoPhoto
    expect(item.input.description).toBeNull()

    await go(item.path)

    const detail = new ItemPage(page, t, "lost")
    await expect(detail.heading(item.title)).toBeVisible()
    await expect(detail.root().getByText(t("item.noDescription"), { exact: true })).toBeVisible()
  })
})

test.describe("reward", () => {
  const without: { key: SharedItemKey; reward: string }[] = [
    { key: "lostZeroReward", reward: "0" },
    { key: "lostNoReward", reward: "null" },
  ]

  for (const { key, reward } of without) {
    test(`a reward of ${reward} shows no badge`, async ({ page, go, t, shared }) => {
      const item = shared.items[key]

      await go(item.path)
      const detail = new ItemPage(page, t, "lost")
      await expect(detail.heading(item.title)).toBeVisible()
      const [before] = t("item.reward", { amount: "\u0000" }).split("\u0000")
      const badge = new RegExp(`^\\s*${escapeRegExp(before.trim())}\\s`)
      await expect(detail.root().getByText(badge)).toHaveCount(0)

      await go(new ListPage(page, t, "lost").path({ search: shared.search.view }))
      const card = new ItemCard(page, t).byTitle(item.title)
      await expect(card).toBeVisible()
      await expect(card.getByText(badge)).toHaveCount(0)
    })
  }
})

test.describe("photo", () => {
  test("a found item's photo opens full screen and closes by click, Escape and the close button", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const item = shared.items.foundPhoto
    await go(item.path)

    const detail = new ItemPage(page, t, "found")
    await expect(detail.photo(item.title)).toBeVisible()
    const dialog = detail.photoDialog(item.title)

    const open = async () => {
      await detail.openPhotoButton().click()
      await expect(dialog).toBeVisible()
      await expect(dialog).toHaveAttribute("data-state", "open")
      await expect(
        dialog.getByRole("img", { name: t("item.photoOf", { title: item.title }) }),
      ).toBeVisible()
    }

    await open()
    await dialog.click()
    await expect(dialog).toBeHidden()

    await open()
    await page.keyboard.press("Escape")
    await expect(dialog).toBeHidden()

    await open()
    await detail.closePhotoButton(item.title).click()
    await expect(dialog).toBeHidden()
    await expect(detail.openPhotoButton()).toBeFocused()
  })

  test("a photo key without an object falls back to the category art", async ({
    page,
    go,
    t,
    shared,
    consoleErrors,
  }) => {
    consoleErrors.allow(
      /Failed to load resource: .*-missing\.png/,
      "The photo is missing on purpose; if the site loads it without /_next/image the URL is the key itself",
    )
    const item = shared.items.foundBrokenPhoto
    await go(item.path)

    const detail = new ItemPage(page, t, "found")
    await expect(detail.heading(item.title)).toBeVisible()
    await expect(detail.noPhoto()).toBeVisible()
    await expect(detail.photo(item.title)).toHaveCount(0)
  })

  test("an item without a photo shows the category art and no full-screen button", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const item = shared.items.lostNoPhoto
    expect(item.input.image).toBeNull()
    await go(item.path)

    const detail = new ItemPage(page, t, "lost")
    await expect(detail.heading(item.title)).toBeVisible()
    await expect(detail.noPhoto()).toBeVisible()
    await expect(detail.openPhotoButton()).toHaveCount(0)
  })
})

test.describe("author's privacy", () => {
  const authors: { kind: ItemKind; key: SharedItemKey }[] = [
    { kind: "lost", key: "lostFull" },
    { kind: "found", key: "foundPhoto" },
  ]

  for (const { kind, key } of authors) {
    test(`the ${kind} author's phone and email are shown masked`, async ({
      page,
      go,
      t,
      shared,
    }) => {
      await go(shared.items[key].path)
      const detail = new ItemPage(page, t, kind)
      await expect(detail.contact("phone")).toContainText("*")
      await expect(detail.contact("email")).toContainText("*")
    })
  }
})

test.describe("JSON-LD", () => {
  test("the full page carries ItemPage and BreadcrumbList, the modal none", async ({
    page,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const item = shared.items.foundPhoto

    await go(item.path)
    const nodes = await readJsonLd(page)
    const itemPage = nodes.find((node) => node["@type"] === "ItemPage")
    expect(itemPage?.name).toBe(item.title)
    const crumbs = nodes.find((node) => node["@type"] === "BreadcrumbList")
    const elements = (crumbs?.itemListElement ?? []) as { item?: string }[]
    expect(elements).toHaveLength(3)
    expect(elements.at(-1)?.item).toMatch(new RegExp(`/${appLocale}/found/${item.id}$`))

    await go(new ListPage(page, t, "found").path({ search: shared.search.view }))
    await new ItemCard(page, t).byTitle(item.title).click()
    const modal = new ItemPage(page, t, "found", { modal: true })
    await expect(modal.heading(item.title)).toBeVisible()
    await expect(jsonLdScripts(modal.root())).toHaveCount(0)
    const types = (await readJsonLd(page)).map((node) => node["@type"])
    expect(types).not.toContain("ItemPage")
  })
})
