import { defaultLocale, type Locale } from "@/i18n/routing"
import de from "./de.json"
import en from "./en.json"
import fr from "./fr.json"
import pl from "./pl.json"
import type { GuideDoc, GuideMeta, GuideSlug, GuideTexts } from "./types"
import uk from "./uk.json"

export type {
  GuideBlock,
  GuideCallout,
  GuideDoc,
  GuideFaqItem,
  GuideMeta,
  GuideSection,
  GuideSlug,
  GuideStep,
  GuideTexts,
} from "./types"

export const GUIDE_SLUGS = [
  "documents",
  "keys",
  "wallet",
  "electronics",
  "animals",
] as const satisfies readonly GuideSlug[]

export const GUIDE_META: Record<GuideSlug, GuideMeta> = {
  documents: {
    category: "DOCUMENTS",
    published: "2026-10-19",
    checked: "2026-10-19",
    related: ["wallet", "keys"],
  },
  keys: {
    category: "KEYS",
    published: "2026-10-19",
    checked: "2026-10-19",
    related: ["wallet", "documents"],
  },
  wallet: {
    category: "WALLET",
    published: "2026-10-19",
    checked: "2026-10-19",
    related: ["documents", "keys"],
  },
  electronics: {
    category: "ELECTRONICS",
    published: "2026-10-19",
    checked: "2026-10-19",
    related: ["wallet", "keys"],
  },
  animals: {
    category: "ANIMALS",
    published: "2026-10-19",
    checked: "2026-10-19",
    related: ["keys", "documents"],
  },
}

const TEXTS = { en, uk, pl, de, fr } satisfies Record<Locale, GuideTexts>

export function isGuideSlug(slug: string): slug is GuideSlug {
  return (GUIDE_SLUGS as readonly string[]).includes(slug)
}

export function guideByCategory(categoryKey: string): GuideSlug | undefined {
  return GUIDE_SLUGS.find((slug) => GUIDE_META[slug].category === categoryKey)
}

export function guideTexts(locale: string): GuideTexts {
  return TEXTS[(locale in TEXTS ? locale : defaultLocale) as Locale]
}

export function guideDoc(locale: string, slug: GuideSlug): GuideDoc {
  return guideTexts(locale).guides[slug]
}
