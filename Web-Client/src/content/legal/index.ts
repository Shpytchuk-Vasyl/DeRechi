import type { Locale } from "@/i18n/routing"
import de from "./de.json"
import en from "./en.json"
import fr from "./fr.json"
import pl from "./pl.json"
import type { LegalDoc, LegalTexts } from "./types"
import uk from "./uk.json"

export type { LegalBlock, LegalDoc } from "./types"
export { LEGAL_CONTACT, LEGAL_UPDATED } from "./types"

const TEXTS = { en, uk, pl, de, fr } satisfies Record<Locale, LegalTexts>

export type LegalKind = "terms" | "privacy"

export function legalTexts(locale: Locale): {
  updated: string
  doc: (kind: LegalKind) => LegalDoc
} {
  const texts = TEXTS[locale]
  return { updated: texts.updated, doc: (kind) => texts[kind] }
}
