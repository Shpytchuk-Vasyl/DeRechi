import { defineRouting } from "next-intl/routing"

export const locales = ["en", "uk", "pl", "de", "fr"] as const

export type Locale = (typeof locales)[number]

export const defaultLocale: Locale = "en"

export const routing = defineRouting({
  locales,
  defaultLocale,
  localePrefix: "always",
  alternateLinks: false,
  localeCookie: {
    name: "DERECHI_LOCALE",
    maxAge: 60 * 60 * 24 * 365,
  },
})
