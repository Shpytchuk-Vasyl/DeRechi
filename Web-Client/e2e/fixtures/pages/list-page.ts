import { expect, type Locator, type Page, type Route } from "@playwright/test"
import type { ItemKind } from "../../support/data"
import type { Translator } from "../../support/i18n"
import { ItemCard } from "./item-card"

export class ListPage {
  readonly cards: ItemCard

  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly kind: ItemKind,
  ) {
    this.cards = new ItemCard(page, t)
  }

  path(query: Record<string, string> = {}): string {
    const search = new URLSearchParams(query).toString()
    return search ? `/${this.kind}?${search}` : `/${this.kind}`
  }

  titles(): Locator {
    return this.cards.all().getByRole("heading", { level: 3 })
  }

  nearbySwitch(): Locator {
    return this.page.getByRole("switch", { name: this.t("list.nearby"), exact: true })
  }

  nearbyProblem(key: "list.nearbyDenied" | "list.nearbyUnavailable"): Locator {
    return this.page.getByRole("status").filter({ hasText: this.t(key) })
  }

  emptyTitle(): Locator {
    return this.page.getByRole("main").getByText(this.t("list.empty"), { exact: true })
  }

  emptyHint(): Locator {
    return this.page.getByRole("main").getByText(this.t("list.emptyHint"), { exact: true })
  }

  emptyReset(): Locator {
    return this.page
      .getByRole("main")
      .getByRole("link", { name: this.t("list.emptyAction"), exact: true })
  }
}

export function location(page: Page): { path: string; query: Record<string, string> } {
  const url = new URL(page.url())
  return { path: url.pathname, query: Object.fromEntries(url.searchParams) }
}

export async function expectListQuery(
  page: Page,
  path: string,
  query: Record<string, string>,
): Promise<void> {
  await expect.poll(() => location(page)).toEqual({ path, query })
}

export type HeldActions = {
  held: Promise<void>
  release(): Promise<void>
}

export async function holdServerActions(page: Page): Promise<HeldActions> {
  let gateOpen: () => void = () => {}
  const gate = new Promise<void>((resolve) => {
    gateOpen = resolve
  })
  let markHeld: () => void = () => {}
  const held = new Promise<void>((resolve) => {
    markHeld = resolve
  })

  let released = false

  await page.route("**/*", async (route: Route) => {
    const request = route.request()
    if (released || request.method() !== "POST" || !(await request.headerValue("next-action"))) {
      await route.fallback()
      return
    }
    markHeld()
    await gate
    await route.fallback()
  })

  return {
    held,
    async release() {
      released = true
      gateOpen()
    },
  }
}
