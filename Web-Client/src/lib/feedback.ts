const TALLY_ORIGIN = "https://tally.so"
const FALLBACK_LOCALE = "uk"

export type FeedbackFields = { page: string }

export function feedbackForm(spec: string | undefined, locale: string): string | undefined {
  const forms = new Map<string, string>()
  for (const entry of (spec ?? "").split(",")) {
    const [key, id] = entry.includes("=") ? entry.split("=", 2) : ["", entry]
    if (id.trim()) forms.set(key.trim(), id.trim())
  }
  return forms.get(locale) ?? forms.get(FALLBACK_LOCALE) ?? forms.values().next().value
}

export function feedbackPage(pathname: string): string {
  return pathname.replace(/^\/claims\/[^/]+/, "/claims")
}

export function feedbackUrl(form: string, fields: FeedbackFields): string {
  const url = new URL(`/r/${encodeURIComponent(form)}`, TALLY_ORIGIN)
  url.searchParams.set("page", fields.page)
  return url.toString()
}
