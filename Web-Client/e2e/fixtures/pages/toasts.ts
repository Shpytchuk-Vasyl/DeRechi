import { expect, type Locator, type Page } from "@playwright/test"
import type { Flags } from "../../support/env"
import type { Translator } from "../../support/i18n"

export class Toasts {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly flags: Flags,
  ) {}

  private dismissButton(scope: Page | Locator): Locator {
    return scope.getByRole("button", { name: this.t("common.dismissNotification"), exact: true })
  }

  byText(text: string): Locator {
    return this.page
      .getByRole("status")
      .or(this.page.getByRole("alert"))
      .filter({ has: this.dismissButton(this.page) })
      .filter({ hasText: text })
  }

  async dismiss(toast: Locator): Promise<void> {
    await this.dismissButton(toast).click()
    await expect(toast).toBeHidden()
  }

  smsOutage(): Locator {
    return this.byText(this.t("smsOutage.title"))
  }

  async dismissSmsOutage(): Promise<void> {
    if (!this.flags.smsOutage) {
      await expect(this.smsOutage()).toHaveCount(0)
      return
    }
    await expect(this.smsOutage()).toBeVisible()
    await this.dismiss(this.smsOutage())
  }
}
