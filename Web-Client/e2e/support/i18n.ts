import { readFileSync } from "node:fs"
import path from "node:path"
import { createTranslator } from "use-intl/core"
import type { Locale } from "./data"

export type Messages = typeof import("../../messages/en.json")

const MESSAGES_DIR = path.resolve(__dirname, "../../messages")
const cache = new Map<Locale, Messages>()

export function messages(locale: Locale): Messages {
  let loaded = cache.get(locale)
  if (!loaded) {
    loaded = JSON.parse(readFileSync(path.join(MESSAGES_DIR, `${locale}.json`), "utf8")) as Messages
    cache.set(locale, loaded)
  }
  return loaded
}

export function translator(locale: Locale) {
  return createTranslator({ locale, messages: messages(locale), timeZone: "Europe/Kyiv" })
}

export type Translator = ReturnType<typeof translator>

export function keyPaths(locale: Locale): string[] {
  const paths: string[] = []
  const walk = (node: unknown, prefix: string) => {
    if (node && typeof node === "object") {
      for (const [key, value] of Object.entries(node))
        walk(value, prefix ? `${prefix}.${key}` : key)
    } else {
      paths.push(prefix)
    }
  }
  walk(messages(locale), "")
  return paths
}

export const RAW_ICU =
  /\{\s*[A-Za-z_]\w*\s*(,\s*(plural|select|selectordinal|number|date|time)\b|\})/

export function untranslated(text: string, locale: Locale): string[] {
  const problems: string[] = []
  const icu = RAW_ICU.exec(text)
  if (icu) problems.push(`raw ICU "${icu[0]}"`)
  for (const key of keyPaths(locale)) {
    if (text.includes(key)) problems.push(`key "${key}"`)
  }
  return problems
}
