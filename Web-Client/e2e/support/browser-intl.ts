import type { Page } from "@playwright/test"
import type { Locale } from "./data"

export function browserNoticeDate(page: Page, locale: Locale, isoDay: string): Promise<string> {
  return page.evaluate(
    ([locale, isoDay]) => {
      const timeZone = "Europe/Kyiv"
      const date = new Date(`${isoDay}T12:00:00Z`)
      const year = (value: Date) =>
        new Intl.DateTimeFormat("en", { timeZone, year: "numeric" }).format(value)
      return new Intl.DateTimeFormat(locale, {
        timeZone,
        day: "numeric",
        month: "long",
        year: year(date) === year(new Date()) ? undefined : "numeric",
      }).format(date)
    },
    [locale, isoDay] as const,
  )
}

export function browserMoney(
  page: Page,
  locale: Locale,
  amount: number,
  currency: string,
): Promise<string> {
  return page.evaluate(
    ([locale, amount, currency]) =>
      new Intl.NumberFormat(locale, {
        style: "currency",
        currency,
        currencyDisplay: "narrowSymbol",
        maximumFractionDigits: 0,
      }).format(amount),
    [locale, amount, currency] as const,
  )
}

export function browserCountryName(page: Page, locale: Locale, code: string): Promise<string> {
  return page.evaluate(
    ([locale, code]) => new Intl.DisplayNames([locale], { type: "region" }).of(code) ?? code,
    [locale, code] as const,
  )
}
