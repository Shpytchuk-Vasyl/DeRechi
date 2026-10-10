import { expect, type Locator, type Page } from "@playwright/test"
import type { ItemKind, Locale } from "../../support/data"
import type { Translator } from "../../support/i18n"

export const LANGUAGE_NAMES: Record<Locale, string> = {
  en: "English",
  uk: "Українська",
  pl: "Polski",
  de: "Deutsch",
  fr: "Français",
}

export type BottomTab = "home" | "lost" | "found"
export type FooterGroup = "footerNotices" | "footerDocuments"
export type FooterLink = "lost" | "found" | "terms" | "privacy" | "safety"

export class Shell {
  constructor(
    readonly page: Page,
    readonly t: Translator,
  ) {}

  private menu(): Locator {
    return this.page.getByRole("navigation", { name: this.t("nav.menu"), exact: true })
  }

  nav(): Locator {
    return this.menu().filter({ visible: true })
  }

  listLink(kind: ItemKind): Locator {
    return this.nav().getByRole("link", { name: this.t(`nav.${kind}`), exact: true })
  }

  header(): Locator {
    return this.page.getByRole("banner")
  }

  brand(): Locator {
    return this.header().getByRole("link", { name: "DeRechi" })
  }

  headerNav(): Locator {
    return this.header().getByRole("navigation", { name: this.t("nav.menu"), exact: true })
  }

  private reportName(kind: ItemKind): string {
    return this.t(kind === "lost" ? "nav.reportLost" : "nav.reportFound")
  }

  headerReport(kind: ItemKind): Locator {
    return this.header().getByRole("link", { name: this.reportName(kind), exact: true })
  }

  languageButton(): Locator {
    return this.header().getByRole("button", { name: this.t("nav.language"), exact: true })
  }

  languageItem(code: Locale): Locator {
    return this.page.getByRole("menuitem", { name: LANGUAGE_NAMES[code], exact: true })
  }

  async switchLocale(code: Locale): Promise<void> {
    await this.languageButton().click()
    await this.languageItem(code).click()
    await expect(this.page).toHaveURL(new RegExp(`/${code}(/|\\?|$)`))
  }

  fab(kind: ItemKind): Locator {
    return this.page
      .getByRole("link", { name: this.reportName(kind), exact: true })
      .and(this.page.locator("a:not(header a):not(main a):not([role=dialog] a)"))
  }

  anyFab(): Locator {
    return this.fab("lost").or(this.fab("found"))
  }

  reportLink(kind: ItemKind): Locator {
    return this.page
      .getByRole("link", { name: this.reportName(kind), exact: true })
      .filter({ visible: true })
      .and(this.page.locator("a:not(main a):not([role=dialog] a)"))
  }

  async openReport(kind: ItemKind): Promise<void> {
    await this.reportLink(kind).click()
    await expect(this.page).toHaveURL(new RegExp(`/[a-z]{2}/report/${kind}(\\?|$)`))
  }

  bottomNav(): Locator {
    return this.menu().filter({
      has: this.page.getByRole("link", { name: this.t("nav.home"), exact: true }),
    })
  }

  bottomTab(tab: BottomTab): Locator {
    return this.bottomNav().getByRole("link", { name: this.t(`nav.${tab}`), exact: true })
  }

  footer(): Locator {
    return this.page.getByRole("contentinfo")
  }

  footerNav(group: FooterGroup): Locator {
    return this.footer().getByRole("navigation", { name: this.t(`nav.${group}`), exact: true })
  }

  footerLink(group: FooterGroup, link: FooterLink): Locator {
    return this.footerNav(group).getByRole("link", { name: this.t(`nav.${link}`), exact: true })
  }
}
