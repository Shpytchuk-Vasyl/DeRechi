import { expect, type Locator, type Page } from "@playwright/test"
import { LOCALES, type Locale } from "./data"
import { siteUrl } from "./site"

export const GLOBAL_NOINDEX = /^noindex,\s*nofollow$/

export function robotsMeta(page: Page): Locator {
  return page.locator('meta[name="robots"]')
}

export function jsonLdScripts(scope: Page | Locator): Locator {
  return scope.locator('script[type="application/ld+json"]')
}

export async function readJsonLd(page: Page): Promise<Record<string, unknown>[]> {
  const texts = await jsonLdScripts(page).allTextContents()
  return texts.flatMap((text, index) => {
    let parsed: Record<string, unknown> | Record<string, unknown>[]
    try {
      parsed = JSON.parse(text)
    } catch (error) {
      throw new Error(`JSON-LD block ${index} is not valid JSON: ${String(error)}\n${text}`)
    }
    return Array.isArray(parsed) ? parsed : [parsed]
  })
}

export async function expectAlternates(page: Page, locale: Locale, path: string): Promise<void> {
  const canonical = page.locator('link[rel="canonical"]')
  await expect(canonical).toHaveCount(1)
  await expect(canonical).toHaveAttribute("href", siteUrl(locale, path))

  const alternates = page.locator('link[rel="alternate"][hreflang]')
  await expect(alternates).toHaveCount(LOCALES.length + 1)
  for (const language of LOCALES) {
    await expect(page.locator(`link[rel="alternate"][hreflang="${language}"]`)).toHaveAttribute(
      "href",
      siteUrl(language, path),
    )
  }
  await expect(page.locator('link[rel="alternate"][hreflang="x-default"]')).toHaveAttribute(
    "href",
    siteUrl("en", path),
  )
}
