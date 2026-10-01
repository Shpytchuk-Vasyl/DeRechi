import { describe, expect, it } from "vitest"
import de from "../../messages/de.json"
import en from "../../messages/en.json"
import fr from "../../messages/fr.json"
import pl from "../../messages/pl.json"
import uk from "../../messages/uk.json"
import { defaultLocale, locales } from "./routing"

type Messages = Record<string, unknown>

const bundles: Record<string, Messages> = { en, uk, pl, de, fr }

function flatten(messages: Messages, prefix = ""): Map<string, string> {
  const flat = new Map<string, string>()
  for (const [key, value] of Object.entries(messages)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === "string") {
      flat.set(path, value)
    } else if (value && typeof value === "object") {
      for (const [nested, nestedValue] of flatten(value as Messages, path)) {
        flat.set(nested, nestedValue)
      }
    }
  }
  return flat
}

function placeholders(message: string): Set<string> {
  const names = new Set<string>()
  for (const match of message.matchAll(/\{\s*([a-zA-Z0-9_]+)\s*[,}]/g)) {
    const name = match[1]
    if (!["plural", "select", "selectordinal", "number", "date", "time"].includes(name)) {
      names.add(name)
    }
  }
  return names
}

const reference = flatten(bundles[defaultLocale])

describe("message bundles", () => {
  it("covers every configured locale", () => {
    expect(Object.keys(bundles).sort()).toEqual([...locales].sort())
  })

  for (const locale of locales) {
    if (locale === defaultLocale) continue

    describe(locale, () => {
      const translated = flatten(bundles[locale])

      it("has no missing keys", () => {
        const missing = [...reference.keys()].filter((key) => !translated.has(key))
        expect(missing).toEqual([])
      })

      it("has no extra keys", () => {
        const extra = [...translated.keys()].filter((key) => !reference.has(key))
        expect(extra).toEqual([])
      })

      it("uses the same arguments as the fallback", () => {
        const mismatched: string[] = []
        for (const [key, value] of reference) {
          const translation = translated.get(key)
          if (translation === undefined) continue

          const expected = [...placeholders(value)].sort()
          const actual = [...placeholders(translation)].sort()
          if (expected.join(",") !== actual.join(",")) {
            mismatched.push(`${key}: expected {${expected}}, got {${actual}}`)
          }
        }
        expect(mismatched).toEqual([])
      })
    })
  }
})
