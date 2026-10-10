import { expect, type Locator, type Page } from "@playwright/test"
import type { PlaceInput } from "../../support/api"
import { type CategoryKey, type ItemKind, MAX_DESCRIPTION } from "../../support/data"
import type { Translator } from "../../support/i18n"
import { Calendar } from "./calendar"
import { ReportForm } from "./report-form"
import { exactText } from "./text"

export const STEP_LABELS = ["details", "whereWhen", "contacts"] as const

export type SavedDraft = {
  at: number
  step: number
  values: {
    title?: string
    description?: string
    date?: string
    categoryId?: string
    place?: PlaceInput
    compensation?: number
    currency?: string
    contact?: { phone?: string; email?: string; socialMedias?: string[] }
    consent?: boolean
  }
}

export function draftKey(kind: ItemKind): string {
  return `report-draft:${kind}`
}

export type FormContacts = { phone: string; email?: string; reward?: number }

export class ReportFlow extends ReportForm {
  readonly calendar: Calendar

  constructor(...args: ConstructorParameters<typeof ReportForm>) {
    super(...args)
    this.calendar = new Calendar(this.page, this.t)
  }

  dialog(): Locator {
    return this.page.getByRole("dialog", {
      name: this.t(this.kind === "lost" ? "form.pageTitleLost" : "form.pageTitleFound"),
      exact: true,
    })
  }

  stepButton(index: number): Locator {
    const name = this.t("form.stepLabel", {
      number: index + 1,
      title: this.t(`form.steps.${STEP_LABELS[index]}`),
    })
    return this.root().getByRole("button", { name, exact: true })
  }

  error(message: string): Locator {
    return this.errors().filter({ hasText: exactText(message) })
  }

  descriptionCounter(length: number): Locator {
    return this.root().getByText(`${length} / ${MAX_DESCRIPTION}`)
  }

  async fillStepDetails(values: {
    title: string
    category: CategoryKey
    description?: string
  }): Promise<void> {
    await this.fillDetails(values)
    await expect(this.categorySelect()).toContainText(this.t(`category.${values.category}`))
  }

  async throughWhereWhen(values: { place: string; photo?: string; date?: string }): Promise<void> {
    await this.next()
    await this.expectStep(1)
    if (values.photo) await this.photo(values.photo)
    if (values.date) await this.pickDate(values.date)
    await this.pickPlace(values.place)
    await this.next()
    await this.expectStep(2)
  }

  async openCalendar(): Promise<void> {
    await this.dateField().click()
    await expect(this.calendar.root()).toBeVisible()
  }

  async pickDate(iso: string): Promise<void> {
    await this.openCalendar()
    await this.calendar.pick(iso)
    await expect(this.calendar.root()).toBeHidden()
  }

  placeOptions(): Locator {
    return this.placeList().getByRole("option")
  }

  placeStatus(message: string): Locator {
    return this.root()
      .getByRole("status")
      .filter({ hasText: exactText(message) })
  }

  async addPlaceHere(name: string): Promise<void> {
    await this.placeSearch().fill(name)
    await this.addHereOption(name).click()
    await expect(this.placeSearch()).toHaveValue(name)
    await expect(this.placeList()).toBeHidden()
  }

  photoPreview(): Locator {
    return this.root().getByRole("img", { name: this.t("upload.preview"), exact: true })
  }

  async dropFile(file: { name: string; mimeType: string; buffer: Buffer }): Promise<void> {
    await this.photoInput().setInputFiles(file)
  }

  async fillStepContacts(values: FormContacts): Promise<void> {
    await this.fillContacts({ ...values, consent: true })
    await expect(this.consent()).toBeChecked()
  }

  preview(): Locator {
    return this.page.locator("aside").filter({
      has: this.page.getByRole("heading", {
        level: 2,
        name: this.t("form.previewTitle"),
        exact: true,
      }),
    })
  }

  previewTitle(): Locator {
    return this.preview().getByRole("heading", { level: 3 })
  }

  previewDate(): Locator {
    return this.preview().getByRole("time")
  }

  readDraft(): Promise<SavedDraft | null> {
    return this.page.evaluate((key) => {
      const raw = sessionStorage.getItem(key)
      return raw ? (JSON.parse(raw) as SavedDraft) : null
    }, draftKey(this.kind))
  }

  async waitForDraft(check: (draft: SavedDraft) => boolean): Promise<SavedDraft> {
    let last: SavedDraft | null = null
    await expect
      .poll(async () => {
        last = await this.readDraft()
        return last !== null && check(last)
      })
      .toBe(true)
    if (!last) throw new Error("waitForDraft(): no draft")
    return last
  }

  async seedDraft(step: number, values: SavedDraft["values"]): Promise<void> {
    await this.page.evaluate(
      ({ key, step, values }) => {
        sessionStorage.setItem(key, JSON.stringify({ at: Date.now(), step, values }))
      },
      { key: draftKey(this.kind), step, values },
    )
  }
}

export async function openReportPage(
  page: Page,
  go: (path: string) => Promise<unknown>,
  t: Translator,
  kind: ItemKind,
): Promise<ReportFlow> {
  await go(`/report/${kind}`)
  const form = new ReportFlow(page, t, kind)
  await form.expectStep(0)
  return form
}

export async function tabTo(page: Page, target: Locator, max = 60): Promise<void> {
  for (let i = 0; i < max; i++) {
    const where = await target.evaluate((element) => {
      const active = document.activeElement
      if (active === element) return "here"
      if (!active || active === document.body) return "after"
      return active.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING
        ? "after"
        : "before"
    })
    if (where === "here") return
    await page.keyboard.press(where === "after" ? "Tab" : "Shift+Tab")
  }
  throw new Error(`tabTo(): ${target} not reached in ${max} key presses`)
}

export async function chooseWithKeyboard(
  page: Page,
  trigger: Locator,
  label: string,
  max = 20,
): Promise<void> {
  await expect(trigger).toBeFocused()
  await page.keyboard.press("Enter")
  const option = page.getByRole("option", { name: label, exact: true })
  await expect(option).toBeVisible()
  for (let i = 0; i < max; i++) {
    const highlighted = await option.evaluate(
      (element) => element === document.activeElement || element.hasAttribute("data-highlighted"),
    )
    if (highlighted) break
    await page.keyboard.press("ArrowDown")
  }
  await page.keyboard.press("Enter")
  await expect(option).toBeHidden()
  await expect(trigger).toContainText(label)
}
