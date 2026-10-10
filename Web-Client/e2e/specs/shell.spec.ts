import { expect, test } from "../fixtures/base"
import { ItemCard, ItemDetail, Shell } from "../fixtures/pages"
import type { BottomTab } from "../fixtures/pages/shell"
import type { ItemKind } from "../support/data"
import { recordForeignHosts } from "../support/hosts"

const KINDS: ItemKind[] = ["lost", "found"]

function listUrl(locale: string, kind: ItemKind): RegExp {
  return new RegExp(`/${locale}/${kind}(\\?|$)`)
}

function homeUrl(locale: string): RegExp {
  return new RegExp(`/${locale}/?(\\?|$)`)
}

test.describe("menu navigation", () => {
  test("Lost and Found lead to the lists, the brand leads home", {
    tag: "@responsive",
  }, async ({ page, go, t, appLocale }) => {
    const shell = new Shell(page, t)
    await go("/")

    await expect(shell.nav()).toHaveCount(1)
    for (const kind of KINDS) {
      await shell.listLink(kind).click()
      await expect(page).toHaveURL(listUrl(appLocale, kind))
      await expect(page.getByRole("heading", { level: 1 })).toHaveText(
        t(kind === "lost" ? "list.lostTitle" : "list.foundTitle"),
      )
    }

    await shell.brand().click()
    await expect(page).toHaveURL(homeUrl(appLocale))
  })

  test("below 768 px: bottom tabs with aria-current and a FAB of the list's kind replace the header nav", {
    tag: "@mobile-only",
  }, async ({ page, go, t, shared, appLocale }) => {
    const shell = new Shell(page, t)
    const stops: { path: string; active: BottomTab; fab: ItemKind | null }[] = [
      { path: "/", active: "home", fab: null },
      { path: "/lost", active: "lost", fab: "lost" },
      { path: "/found", active: "found", fab: "found" },
      { path: shared.items.lostFull.path, active: "lost", fab: "lost" },
      { path: shared.items.foundPhoto.path, active: "found", fab: "found" },
    ]

    for (const stop of stops) {
      await test.step(stop.path, async () => {
        await go(stop.path)
        await expect(shell.headerNav()).toBeHidden()
        for (const kind of KINDS) await expect(shell.headerReport(kind)).toBeHidden()
        await expect(shell.bottomNav()).toBeVisible()
        for (const tab of ["home", "lost", "found"] as const) {
          const link = shell.bottomTab(tab)
          await expect(link).toBeVisible()
          if (tab === stop.active) await expect(link).toHaveAttribute("aria-current", "page")
          else await expect(link).not.toHaveAttribute("aria-current", /.+/)
        }

        if (stop.fab === null) {
          await expect(shell.anyFab()).toHaveCount(0)
        } else {
          const other: ItemKind = stop.fab === "lost" ? "found" : "lost"
          await expect(shell.fab(stop.fab)).toBeVisible()
          await expect(shell.fab(stop.fab)).toHaveAttribute(
            "href",
            `/${appLocale}/report/${stop.fab}`,
          )
          await expect(shell.fab(other)).toHaveCount(0)
        }
      })
    }

    await go("/")
    await shell.bottomTab("found").click()
    await expect(page).toHaveURL(listUrl(appLocale, "found"))
    await shell.bottomTab("lost").click()
    await expect(page).toHaveURL(listUrl(appLocale, "lost"))
    await shell.bottomTab("home").click()
    await expect(page).toHaveURL(homeUrl(appLocale))
  })

  test("at 768 px the header nav and report button show, the bottom bar and the FAB do not", {
    tag: "@tablet-only",
  }, async ({ page, go, t }) => {
    const shell = new Shell(page, t)
    await go("/lost")

    await expect(shell.headerNav()).toBeVisible()
    await expect(shell.headerReport("lost")).toBeVisible()
    await expect(shell.bottomNav()).toBeHidden()
    await expect(shell.anyFab()).toHaveCount(0)
  })
})

test.describe("header report button", () => {
  test("shows only on /lost and /found, of the list's kind", async ({
    page,
    go,
    t,
    shared,
    appLocale,
  }) => {
    const shell = new Shell(page, t)

    for (const kind of KINDS) {
      const other: ItemKind = kind === "lost" ? "found" : "lost"
      await go(`/${kind}`)
      await expect(shell.headerReport(kind)).toBeVisible()
      await expect(shell.headerReport(kind)).toHaveAttribute("href", `/${appLocale}/report/${kind}`)
      await expect(shell.headerReport(other)).toHaveCount(0)
    }

    for (const path of ["/", shared.items.lostFull.path, "/report/lost"]) {
      await go(path)
      await expect(page.getByRole("main")).toBeVisible()
      for (const kind of KINDS) await expect(shell.headerReport(kind)).toHaveCount(0)
    }
  })
})

test.describe("footer", () => {
  test("Notices and Documents link where they should", async ({ page, go, t, appLocale }) => {
    const shell = new Shell(page, t)
    await go("/")

    const links = [
      { group: "footerNotices", link: "lost", path: "/lost" },
      { group: "footerNotices", link: "found", path: "/found" },
      { group: "footerDocuments", link: "terms", path: "/terms" },
      { group: "footerDocuments", link: "privacy", path: "/privacy" },
      { group: "footerDocuments", link: "safety", path: "/safety" },
    ] as const

    await expect(shell.footerNav("footerNotices").getByRole("link")).toHaveCount(2)
    await expect(shell.footerNav("footerDocuments").getByRole("link")).toHaveCount(3)
    for (const { group, link, path } of links) {
      await expect(shell.footerLink(group, link)).toHaveAttribute("href", `/${appLocale}${path}`)
    }

    for (const { group, link, path } of links) {
      await shell.footerLink(group, link).click()
      await expect(page).toHaveURL(new RegExp(`/${appLocale}${path}(\\?|$)`))
      await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toBeVisible()
    }
  })
})

test.describe("external requests", () => {
  test("key pages talk only to the site, the API and the files host", async ({
    page,
    go,
    t,
    shared,
  }) => {
    const foreign = recordForeignHosts(page.context())
    const item = shared.items.foundPhoto

    await go("/")
    await expect(page).toHaveTitle(t("app.name"))
    await go(`/found?search=${shared.search.view}`)
    await expect(new ItemCard(page, t).byTitle(item.title)).toBeVisible()
    await go(item.path)
    await expect(new ItemDetail(page, t, "found").heading(item.title)).toBeVisible()
    await go("/report/found")
    await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toBeVisible()
    await go("/safety")
    await expect(page.getByRole("main")).toBeVisible()

    expect(foreign(), "hosts outside env.allowedHosts").toEqual([])
  })
})
