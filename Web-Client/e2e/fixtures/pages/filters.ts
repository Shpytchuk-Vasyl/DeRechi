import { expect, type Locator, type Page } from "@playwright/test"
import type { ItemSort } from "../../support/api"
import type { CategoryKey } from "../../support/data"
import type { Translator } from "../../support/i18n"
import { isMobileLayout } from "../../support/viewport"
import { Calendar } from "./calendar"

export class Filters {
  readonly calendar: Calendar

  constructor(
    readonly page: Page,
    readonly t: Translator,
  ) {
    this.calendar = new Calendar(page, t)
  }

  private get mobile(): boolean {
    return isMobileLayout(this.page)
  }

  private fields(): Locator {
    return this.mobile ? this.sheet() : this.page.getByRole("main")
  }

  sheet(): Locator {
    return this.page.getByRole("dialog", { name: this.t("list.filters"), exact: true })
  }

  sheetButton(): Locator {
    return this.page.getByRole("button", { name: this.t("list.filters"), exact: true })
  }

  activeCount(): Locator {
    return this.page.locator("form").filter({ has: this.sheetButton() }).getByText(/^\d+$/)
  }

  async openSheet(): Promise<void> {
    if (!this.mobile) return
    if (await this.sheet().isVisible()) return
    await this.sheetButton().click()
    await expect(this.sheet()).toBeVisible()
  }

  searchBox(): Locator {
    return this.page
      .getByRole("searchbox", { name: this.t("list.search"), exact: true })
      .filter({ visible: true })
  }

  async search(text: string): Promise<void> {
    await this.searchBox().fill(text)
  }

  categorySelect(): Locator {
    return this.fields()
      .getByRole("combobox", { name: this.t("list.category"), exact: true })
      .filter({ visible: true })
  }

  async category(key: CategoryKey | null): Promise<void> {
    await this.openSheet()
    await this.categorySelect().click()
    const label = key ? this.t(`category.${key}`) : this.t("list.allCategories")
    await this.page.getByRole("option", { name: label, exact: true }).click()
  }

  sortButton(): Locator {
    return this.page
      .getByRole("main")
      .getByRole("button", { name: this.t("list.sort"), exact: true })
      .filter({ visible: true })
  }

  async sort(value: ItemSort): Promise<void> {
    const label = this.t(`sort.${value}`)
    if (this.mobile) {
      await this.openSheet()
      await this.sheet()
        .getByRole("combobox", { name: this.t("list.sort"), exact: true })
        .click()
      await this.page.getByRole("option", { name: label, exact: true }).click()
      await this.apply()
      return
    }
    await this.sortButton().click()
    await this.page.getByRole("menuitem", { name: label, exact: true }).click()
  }

  datesTrigger(): Locator {
    return this.fields()
      .getByRole("button", { name: this.t("list.dateRange"), exact: true })
      .filter({ visible: true })
  }

  datesClearButton(): Locator {
    return this.datesTrigger()
      .locator("xpath=..")
      .getByRole("button", { name: this.t("list.reset"), exact: true })
  }

  async openDates(): Promise<void> {
    await this.openSheet()
    if (await this.calendar.root().isVisible()) return
    await this.datesTrigger().click()
    await expect(this.calendar.root()).toBeVisible()
  }

  async closeDates(): Promise<void> {
    if (this.mobile) return
    await this.page.keyboard.press("Escape")
    await expect(this.calendar.root()).toBeHidden()
  }

  async apply(): Promise<void> {
    if (!this.mobile) {
      await this.page.getByRole("button", { name: this.t("list.apply"), exact: true }).click()
      return
    }
    if (await this.sheet().isVisible()) {
      await this.sheet()
        .getByRole("button", { name: this.t("list.showResults"), exact: true })
        .click()
      await expect(this.sheet()).toBeHidden()
      return
    }
    await this.searchBox().press("Enter")
  }

  resetButton(): Locator {
    return this.fields()
      .getByRole("button", { name: this.t("list.reset"), exact: true })
      .filter({ visible: true })
      .last()
  }

  async reset(): Promise<void> {
    await this.openSheet()
    await this.resetButton().click()
  }

  applyHint(): Locator {
    return this.page.getByText(this.t("list.applyHint"), { exact: true })
  }
}
