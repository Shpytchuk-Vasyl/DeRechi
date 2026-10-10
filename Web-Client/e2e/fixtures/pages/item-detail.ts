import type { Locator, Page } from "@playwright/test"
import type { CategoryKey, ItemKind } from "../../support/data"
import type { Translator } from "../../support/i18n"
import { exactText } from "./text"

export class ItemDetail {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly kind: ItemKind,
    readonly options: { modal?: boolean } = {},
  ) {}

  root(): Locator {
    return this.options.modal ? this.page.getByRole("dialog") : this.page.getByRole("main")
  }

  heading(title?: string): Locator {
    return this.root().getByRole("heading", { level: 1, ...(title ? { name: title } : {}) })
  }

  categoryBadge(key: CategoryKey): Locator {
    return this.root().getByText(this.t(`category.${key}`), { exact: true })
  }

  reward(amount: string): Locator {
    return this.root().getByText(this.t("item.reward", { amount }), { exact: true })
  }

  protected definition(term: string): Locator {
    return this.root()
      .locator("dt", { hasText: exactText(term) })
      .locator("xpath=following-sibling::dd[1]")
  }

  date(): Locator {
    return this.definition(this.t(this.kind === "lost" ? "item.lostOn" : "item.foundOn"))
  }

  place(): Locator {
    return this.definition(this.t("item.place"))
  }

  mapsLink(): Locator {
    return this.root().getByRole("link", { name: this.t("item.openInMaps"), exact: true })
  }

  descriptionHeading(): Locator {
    return this.root().getByRole("heading", { level: 2, name: this.t("item.description") })
  }

  openPhotoButton(): Locator {
    return this.root().getByRole("button", { name: this.t("item.openPhoto"), exact: true })
  }

  breadcrumb(): Locator {
    return this.root().getByRole("link", {
      name: this.t(this.kind === "lost" ? "item.lost" : "item.found"),
      exact: true,
    })
  }
}
