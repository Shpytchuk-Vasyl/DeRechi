import { expect, type Locator, type Page } from "@playwright/test"
import type { CategoryKey, ItemKind } from "../../support/data"
import type { Translator } from "../../support/i18n"

export class ReportForm {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly kind: ItemKind,
    readonly options: { modal?: boolean } = {},
  ) {}

  root(): Locator {
    return this.options.modal ? this.page.getByRole("dialog") : this.page.getByRole("main")
  }

  async expectStep(index: number): Promise<void> {
    await expect(
      this.root().getByRole("navigation", {
        name: this.t("form.stepOf", { current: index + 1, total: 3 }),
        exact: true,
      }),
    ).toBeVisible()
  }

  nextButton(): Locator {
    return this.root().getByRole("button", { name: this.t("form.next"), exact: true })
  }

  backButton(): Locator {
    return this.root().getByRole("button", { name: this.t("form.back"), exact: true })
  }

  cancelButton(): Locator {
    return this.root().getByRole("button", { name: this.t("form.cancel"), exact: true })
  }

  publishButton(): Locator {
    return this.root().getByRole("button", { name: this.t("form.publish"), exact: true })
  }

  async next(): Promise<void> {
    await this.nextButton().click()
  }

  async back(): Promise<void> {
    await this.backButton().click()
  }

  async publish(): Promise<string> {
    await this.publishButton().click()
    const url = new RegExp(`/[a-z]{2}/${this.kind}/(\\d+)(\\?|$)`)
    await expect(this.page).toHaveURL(url, { timeout: 30_000 })
    const id = url.exec(this.page.url())?.[1]
    if (!id) throw new Error(`publish(): no item id in ${this.page.url()}`)
    return id
  }

  title(): Locator {
    return this.root().getByLabel(
      this.t(this.kind === "lost" ? "form.titleLost" : "form.titleFound"),
      { exact: true },
    )
  }

  categorySelect(): Locator {
    return this.root().getByRole("combobox", { name: this.t("form.category"), exact: true })
  }

  async category(key: CategoryKey): Promise<void> {
    await this.categorySelect().click()
    await this.page.getByRole("option", { name: this.t(`category.${key}`), exact: true }).click()
  }

  description(): Locator {
    return this.root().getByLabel(this.t("form.description"), { exact: true })
  }

  async fillDetails(values: {
    title: string
    category: CategoryKey
    description?: string
  }): Promise<void> {
    await this.title().fill(values.title)
    await this.category(values.category)
    if (values.description !== undefined) await this.description().fill(values.description)
  }

  photoInput(): Locator {
    return this.root().locator('input[type="file"]')
  }

  async photo(path: string): Promise<void> {
    await this.photoInput().setInputFiles(path)
    await expect(
      this.root().getByRole("img", { name: this.t("upload.preview"), exact: true }),
    ).toBeVisible()
  }

  removePhotoButton(): Locator {
    return this.root().getByRole("button", { name: this.t("upload.remove"), exact: true })
  }

  dateField(): Locator {
    return this.root().getByLabel(
      this.t(this.kind === "lost" ? "form.dateLost" : "form.dateFound"),
      { exact: true },
    )
  }

  placeSearch(): Locator {
    return this.root().getByRole("combobox", { name: this.t("form.place"), exact: true })
  }

  placeList(): Locator {
    return this.root().getByRole("listbox", { name: this.t("form.place"), exact: true })
  }

  async pickPlace(name: string): Promise<void> {
    await this.placeSearch().fill(name)
    const option = this.placeList().getByRole("option", { name, exact: true })
    await option.click()
    await expect(this.placeSearch()).toHaveValue(name)
  }

  addHereOption(name: string): Locator {
    return this.placeList().getByRole("option", {
      name: this.t("form.placeAddHere", { name }),
    })
  }

  reward(): Locator {
    return this.root().getByLabel(this.t("form.reward"), { exact: true })
  }

  currencySelect(): Locator {
    return this.root().getByRole("combobox", { name: this.t("form.currency"), exact: true })
  }

  async currency(code: string): Promise<void> {
    await this.currencySelect().click()
    await this.page.getByRole("option", { name: code, exact: true }).click()
  }

  phone(): Locator {
    return this.root().getByLabel(this.t("form.phone"), { exact: true })
  }

  email(): Locator {
    return this.root().getByLabel(this.t("form.emailOptional"), { exact: true })
  }

  consent(): Locator {
    const name = this.t.markup("form.consent", { terms: (c) => c, privacy: (c) => c })
    return this.root().getByRole("checkbox", { name })
  }

  async fillContacts(values: {
    phone: string
    email?: string
    reward?: number
    consent?: boolean
  }): Promise<void> {
    if (values.reward !== undefined) await this.reward().fill(String(values.reward))
    await this.phone().fill(values.phone)
    if (values.email !== undefined) await this.email().fill(values.email)
    if (values.consent ?? true) await this.consent().check()
  }

  errors(): Locator {
    return this.root().getByRole("alert").filter({ visible: true })
  }
}
