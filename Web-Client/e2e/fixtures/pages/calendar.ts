import { expect, type Locator, type Page } from "@playwright/test"
import type { Translator } from "../../support/i18n"

const DAY_CELL = '[role="gridcell"][data-day]:not([data-hidden]):not([data-outside])'

export class Calendar {
  constructor(
    readonly page: Page,
    readonly t: Translator,
  ) {}

  root(): Locator {
    return this.page.locator('[data-slot="calendar"]').filter({ visible: true })
  }

  day(iso: string): Locator {
    return this.root().locator(`${DAY_CELL}[data-day="${iso}"]`)
  }

  dayButton(iso: string): Locator {
    return this.day(iso).getByRole("button")
  }

  private monthButton(back: boolean): Locator {
    return this.root().getByRole("button", {
      name: this.t(back ? "calendar.previousMonth" : "calendar.nextMonth"),
      exact: true,
    })
  }

  async showMonthOf(iso: string): Promise<void> {
    for (let step = 0; step < 6; step++) {
      if ((await this.day(iso).count()) > 0) return
      const first = this.root().locator(DAY_CELL).first()
      const shown = await first.getAttribute("data-day")
      if (!shown) throw new Error("showMonthOf(): the calendar shows no days")
      await this.monthButton(iso < shown).click()
      await expect(first).not.toHaveAttribute("data-day", shown)
    }
    throw new Error(`showMonthOf(): ${iso} is not reachable`)
  }

  async pick(iso: string): Promise<void> {
    await this.showMonthOf(iso)
    await this.dayButton(iso).click()
  }
}
