import type { Locale } from "@/i18n/routing"
import de from "./de.json"
import en from "./en.json"
import fr from "./fr.json"
import DE from "./jurisdictions/DE.json"
import FR from "./jurisdictions/FR.json"
import PL from "./jurisdictions/PL.json"
import UA from "./jurisdictions/UA.json"
import pl from "./pl.json"
import type { Jurisdiction, JurisdictionTexts, LegalDoc, LegalTexts } from "./types"
import uk from "./uk.json"

export type { Jurisdiction, JurisdictionTexts, LegalBlock, LegalDoc } from "./types"
export { LEGAL_CONTACT } from "./types"

const TEXTS = { en, uk, pl, de, fr } satisfies Record<Locale, LegalTexts>

const JURISDICTIONS: Jurisdiction[] = [UA, PL, DE, FR]

export type LegalKind = "terms" | "privacy"

export function jurisdiction(countryCode: string): Jurisdiction {
  const found = JURISDICTIONS.find((candidate) => candidate.code === countryCode)
  if (found) return found
  console.warn(`No legal texts for country ${countryCode}, showing ${JURISDICTIONS[0].code}`)
  return JURISDICTIONS[0]
}

export function legalTexts(
  locale: Locale,
  countryCode: string,
): {
  updated: string
  updatedOn: string
  country: string
  slots: JurisdictionTexts
  doc: (kind: LegalKind) => LegalDoc
} {
  const texts = TEXTS[locale]
  const law = jurisdiction(countryCode)
  return {
    updated: texts.updated,
    updatedOn: law.updated,
    country: law.code,
    slots: law.texts[locale],
    doc: (kind) => texts[kind],
  }
}
