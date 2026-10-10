import type { Locator, Page } from "@playwright/test"
import type { Translator } from "../../support/i18n"

export class ItemCard {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly scope: Locator = page.getByRole("main"),
  ) {}

  all(): Locator {
    return this.scope
      .getByRole("link")
      .filter({ has: this.page.getByRole("heading", { level: 3 }) })
  }

  byTitle(title: string): Locator {
    return this.scope.getByRole("link").filter({
      has: this.page.getByRole("heading", { name: title, exact: true, level: 3 }),
    })
  }

  date(card: Locator): Locator {
    return card.getByRole("time")
  }

  counter(shown: number): Locator {
    return this.scope.getByText(this.t("list.showingSome", { shown }), { exact: true })
  }

  showMoreButton(): Locator {
    return this.scope.getByRole("button", { name: this.t("list.showMore"), exact: true })
  }
}
