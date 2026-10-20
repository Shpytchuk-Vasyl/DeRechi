import { readFileSync } from "node:fs"
import path from "node:path"
import type { Locale } from "./data"
import { WEB_CLIENT_DIR } from "./env"

type GuideTexts = { faqTitle: string }

export function guideTexts(locale: Locale): GuideTexts {
  const file = path.join(WEB_CLIENT_DIR, `src/content/guides/${locale}.json`)
  return JSON.parse(readFileSync(file, "utf8")) as GuideTexts
}
