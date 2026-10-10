import { expect, type Page } from "@playwright/test"
import { ItemCard } from "../fixtures/pages/item-card"
import { ItemDetail } from "../fixtures/pages/item-detail"
import { ReportForm } from "../fixtures/pages/report-form"
import { ITEM_NAMES } from "./data"
import type { Translator } from "./i18n"
import type { SharedData, SharedItem } from "./shared"

export type Go = (path: string) => Promise<unknown>

export type WalkContext = {
  page: Page
  t: Translator
  go: Go
  shared: SharedData
}

export type Visit = (label: string) => Promise<void>

export async function openHome({ page, t, go }: WalkContext): Promise<void> {
  await go("/")
  await expect(page.getByRole("main")).toBeVisible()
  await expect(page).toHaveTitle(t("app.name"))
}

export async function openList(ctx: WalkContext, item: SharedItem): Promise<void> {
  await ctx.go(`/${item.kind}?search=${ctx.shared.search.view}`)
  await expect(new ItemCard(ctx.page, ctx.t).byTitle(item.title)).toBeVisible()
}

export async function openDetails(ctx: WalkContext, item: SharedItem): Promise<void> {
  await ctx.go(item.path)
  await expect(new ItemDetail(ctx.page, ctx.t, item.kind).heading(item.title)).toBeVisible()
}

export async function openSafety({ page, t, go }: WalkContext): Promise<void> {
  await go("/safety")
  await expect(page).toHaveTitle(t("app.titleTemplate", { page: t("safety.title") }))
}

export async function openTerms({ page, go }: WalkContext): Promise<void> {
  await go("/terms")
  await expect(page.getByRole("main").getByRole("heading", { level: 1 })).toBeVisible()
}

export async function walkReport(ctx: WalkContext, visit: Visit): Promise<void> {
  const { page, t, go, shared } = ctx
  const form = new ReportForm(page, t, "lost")
  await go("/report/lost")
  await form.expectStep(0)
  await visit("report step 1")

  await form.fillDetails({ title: `${shared.marker} ${ITEM_NAMES.uk.KEYS}`, category: "KEYS" })
  await form.next()
  await form.expectStep(1)
  await visit("report step 2")

  await form.pickPlace(shared.place.name)
  await form.next()
  await form.expectStep(2)
  await visit("report step 3")
}

export async function walkKeyPages(ctx: WalkContext, visit: Visit): Promise<void> {
  const item = ctx.shared.items.lostFull

  await openHome(ctx)
  await visit("home")

  await openList(ctx, item)
  await visit("list")

  await openDetails(ctx, item)
  await visit("details")

  await walkReport(ctx, visit)

  await openSafety(ctx)
  await visit("safety")

  await openTerms(ctx)
  await visit("terms")
}
