import { expect, type Locator, type Page } from "@playwright/test"
import type { Translator } from "../../support/i18n"
import { dialogShape, isSheet } from "../../support/viewport"

export type CloseBy = "button" | "escape" | "overlay"

export class RouteDialog {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly root: Locator = page.getByRole("dialog"),
  ) {}

  closeButton(): Locator {
    return this.root.getByRole("button", { name: this.t("common.close"), exact: true })
  }

  async expectOpen(): Promise<void> {
    await expect(this.root).toBeVisible()
    await expect(this.root).toHaveAttribute("data-state", "open")
  }

  async expectShape(): Promise<void> {
    await expect
      .poll(() => dialogShape(this.page, this.root), { message: "the dialog's shape" })
      .toBe(isSheet(this.page) ? "sheet" : "centered")
  }

  async close(by: CloseBy): Promise<void> {
    if (by === "button") await this.closeButton().click()
    else if (by === "escape") await this.page.keyboard.press("Escape")
    else await this.page.mouse.click(4, 4)
    await expect(this.root).toBeHidden()
  }
}
