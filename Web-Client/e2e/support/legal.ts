import { existsSync, readFileSync } from "node:fs"
import path from "node:path"
import type { Locale } from "./data"
import { WEB_CLIENT_DIR } from "./env"

export type LegalSlot = "findersLaw" | "governingLaw" | "dataLaw" | "rightsBasis" | "complaintRight"

type Jurisdiction = {
  code: string
  updated: string
  texts: Record<Locale, Record<LegalSlot, string>>
}

const DIR = path.join(WEB_CLIENT_DIR, "src/content/legal/jurisdictions")

export const FALLBACK_JURISDICTION = "UA"

export const DISTINCT_SLOT = { terms: "governingLaw", privacy: "complaintRight" } as const

const cache = new Map<string, Jurisdiction | null>()

function load(code: string): Jurisdiction | null {
  if (!cache.has(code)) {
    const file = path.join(DIR, `${code}.json`)
    cache.set(
      code,
      existsSync(file) ? (JSON.parse(readFileSync(file, "utf8")) as Jurisdiction) : null,
    )
  }
  return cache.get(code) ?? null
}

export function hasJurisdiction(code: string): boolean {
  return load(code) !== null
}

export function jurisdictionOf(code: string): string {
  return hasJurisdiction(code) ? code : FALLBACK_JURISDICTION
}

export function legalSlotText(code: string, locale: Locale, slot: LegalSlot): string {
  const law = load(jurisdictionOf(code))
  if (!law) throw new Error(`e2e: no legal texts for ${code} or ${FALLBACK_JURISDICTION}`)
  const text = law.texts[locale][slot]
  return text
    .split(/\{\w+\}/)
    .map((part) => part.trim())
    .reduce((longest, part) => (part.length > longest.length ? part : longest), "")
}
