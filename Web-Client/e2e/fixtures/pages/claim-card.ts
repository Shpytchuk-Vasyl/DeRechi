import { expect, type Locator, type Page } from "@playwright/test"
import type { ContactInput } from "../../support/api"
import type { ItemKind } from "../../support/data"
import type { Translator } from "../../support/i18n"

export type FormMessenger = "TELEGRAM" | "VIBER" | "WHATSAPP"

export class ClaimCard {
  constructor(
    readonly page: Page,
    readonly t: Translator,
    readonly kind: ItemKind,
    readonly root: Locator = page.getByRole("main"),
  ) {}

  title(): Locator {
    return this.root.getByText(
      this.t(this.kind === "lost" ? "item.contactTitle" : "item.contactFinderTitle"),
      { exact: true },
    )
  }

  openButton(): Locator {
    return this.root.getByRole("button", {
      name: this.t(this.kind === "lost" ? "claim.sendFound" : "claim.sendMine"),
      exact: true,
    })
  }

  async open(): Promise<void> {
    await this.openButton().click()
    await expect(this.phone()).toBeVisible()
  }

  phone(): Locator {
    return this.root.getByLabel(this.t("form.phone"), { exact: true })
  }

  email(): Locator {
    return this.root.getByLabel(this.t("form.email"), { exact: true })
  }

  messenger(channel: FormMessenger): Locator {
    return this.root.getByRole("checkbox", { name: this.t(`form.social.${channel}`), exact: true })
  }

  consent(): Locator {
    const name = this.t.markup("claim.consent", { terms: (c) => c, privacy: (c) => c })
    return this.root.getByRole("checkbox", { name })
  }

  legalLink(which: "terms" | "privacy"): Locator {
    return this.root.locator(`form a[href*="/${which}?country="]`)
  }

  async fill(
    contact: Pick<ContactInput, "phone" | "email">,
    { consent = true, messengers = [] }: { consent?: boolean; messengers?: FormMessenger[] } = {},
  ): Promise<void> {
    await this.phone().fill(contact.phone)
    await this.email().fill(contact.email)
    for (const channel of messengers) await this.messenger(channel).check()
    if (consent) await this.consent().check()
  }

  submitButton(): Locator {
    return this.root.getByRole("button", { name: this.t("claim.submit"), exact: true })
  }

  async submit(): Promise<void> {
    await this.submitButton().click()
  }

  done(): Locator {
    return this.root.getByRole("heading", { name: this.t("claim.doneTitle"), exact: true })
  }

  repeated(): Locator {
    return this.root.getByText(this.t("claim.repeated"), { exact: true })
  }

  errors(): Locator {
    return this.root.getByRole("alert")
  }
}
