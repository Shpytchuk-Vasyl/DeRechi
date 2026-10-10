import type { Locator, Page } from "@playwright/test"
import type { ItemKind } from "../../support/data"
import type { Translator } from "../../support/i18n"
import { isWide } from "../../support/viewport"
import { escapeRegExp } from "./text"

export const HOME_CHIPS = ["DOCUMENTS", "KEYS", "WALLET", "BAGS"] as const
export type HomeChip = (typeof HOME_CHIPS)[number]

export const FAQ_NUMBERS = [1, 2, 3, 4, 5] as const
export type FaqNumber = (typeof FAQ_NUMBERS)[number]

export const BENEFIT_NUMBERS = [1, 2, 3, 4, 5] as const
export type BenefitNumber = (typeof BENEFIT_NUMBERS)[number]

export const RAIL_SIZE = 5

export class HomePage {
  constructor(
    readonly page: Page,
    readonly t: Translator,
  ) {}

  main(): Locator {
    return this.page.getByRole("main")
  }

  heroHeading(): Locator {
    return this.main().getByRole("heading", {
      level: 1,
      name: this.t("home.heroTitle"),
      exact: true,
    })
  }

  heroReportLink(kind: ItemKind): Locator {
    const name = this.t(kind === "lost" ? "nav.reportLost" : "nav.reportFound")
    return this.main().getByRole("link", { name, exact: true }).first()
  }

  searchBox(): Locator {
    return this.main().getByRole("searchbox", { name: this.t("home.searchWhat"), exact: true })
  }

  chip(key: HomeChip): Locator {
    return this.main().getByRole("link", { name: this.t(`category.${key}`), exact: true })
  }

  private railName(kind: ItemKind): string {
    return this.t(kind === "found" ? "home.recentFound" : "home.recentLost")
  }

  rail(kind: ItemKind): Locator {
    return this.main().getByRole("region", { name: this.railName(kind), exact: true })
  }

  railHeading(kind: ItemKind): Locator {
    return this.rail(kind).getByRole("heading", {
      level: 2,
      name: this.railName(kind),
      exact: true,
    })
  }

  seeAllLink(kind: ItemKind): Locator {
    return this.rail(kind).getByRole("link", {
      name: this.t(kind === "found" ? "home.seeAllFound" : "home.seeAllLost"),
      exact: true,
    })
  }

  railError(kind: ItemKind): Locator {
    return this.rail(kind).getByText(
      this.t(kind === "found" ? "home.recentFoundError" : "home.recentLostError"),
      { exact: true },
    )
  }

  railCards(kind: ItemKind): Locator {
    return this.rail(kind)
      .getByRole("link")
      .filter({ has: this.page.getByRole("heading", { level: 3 }) })
  }

  railVisibleMax(): number {
    return isWide(this.page) ? RAIL_SIZE : RAIL_SIZE - 1
  }

  benefits(): Locator {
    return this.main().getByRole("region", { name: this.t("home.benefitsTitle"), exact: true })
  }

  tabs(): Locator {
    return this.benefits().getByRole("tab")
  }

  benefitTitle(n: BenefitNumber): string {
    return this.t(`home.benefit${n}Title`)
  }

  tab(n: BenefitNumber): Locator {
    return this.benefits().getByRole("tab", {
      name: new RegExp(`^\\s*${escapeRegExp(this.benefitTitle(n))}`),
    })
  }

  tabPanel(): Locator {
    return this.benefits().getByRole("tabpanel")
  }

  faq(): Locator {
    return this.main().getByRole("region", { name: this.t("home.faqTitle"), exact: true })
  }

  faqHeading(): Locator {
    return this.faq().getByRole("heading", {
      level: 2,
      name: this.t("home.faqTitle"),
      exact: true,
    })
  }

  faqQuestion(n: FaqNumber): Locator {
    return this.faq().getByRole("button", { name: this.t(`home.faqQ${n}`), exact: true })
  }

  faqAnswer(n: FaqNumber): Locator {
    return this.faq().getByRole("region", { name: this.t(`home.faqQ${n}`), exact: true })
  }

  faqExpanded(): Locator {
    return this.faq().getByRole("button", { expanded: true })
  }

  mascot(): Locator {
    return this.page.getByRole("button", { name: this.t("game.catch"), exact: true })
  }
}
