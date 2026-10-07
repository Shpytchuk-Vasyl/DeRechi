import type { Locale } from "@/i18n/routing"

export type LegalBlock = string | string[]

export type LegalSection = {
  id?: string
  heading: string
  body: LegalBlock[]
}

export type LegalDoc = {
  title: string
  summary: string
  sections: LegalSection[]
}

export type LegalTexts = {
  updated: string
  terms: LegalDoc
  privacy: LegalDoc
}

export type JurisdictionTexts = {
  findersLaw: string
  governingLaw: string
  dataLaw: string
  rightsBasis: string
  complaintRight: string
  withdrawalLaw: string
}

export type Jurisdiction = {
  code: string
  updated: string
  texts: Record<Locale, JurisdictionTexts>
}

export const LEGAL_CONTACT = {
  operator: "DeRechi",
  email: "derechi.support@gmail.com",
}
