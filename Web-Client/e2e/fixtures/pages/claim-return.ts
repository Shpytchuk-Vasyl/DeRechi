import type { Locator, Page } from "@playwright/test"
import type { Translator } from "../../support/i18n"

export class ClaimReturnPage {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly root: Locator = page.getByRole("main"),
  ) {}

  private heading(name: string): Locator {
    return this.root.getByRole("heading", { name, exact: true })
  }

  title(): Locator {
    return this.heading(this.t("claim.confirm.title"))
  }

  confirmButton(): Locator {
    return this.root.getByRole("button", { name: this.t("claim.confirm.button"), exact: true })
  }

  async confirm(): Promise<void> {
    await this.confirmButton().click()
  }

  invalidTitle(): Locator {
    return this.heading(this.t("claim.confirm.invalidTitle"))
  }

  homeLink(): Locator {
    return this.root.getByRole("link", { name: this.t("claim.confirm.home"), exact: true })
  }
}
