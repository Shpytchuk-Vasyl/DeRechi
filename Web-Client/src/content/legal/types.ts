export type LegalBlock = string | string[]

export type LegalSection = {
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

export const LEGAL_CONTACT = {
  operator: "[OPERATOR NAME]",
  email: "[CONTACT EMAIL]",
}

export const LEGAL_UPDATED = "2026-09-25"
