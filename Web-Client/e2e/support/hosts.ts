import type { BrowserContext } from "@playwright/test"
import { allowedHosts, blockedHosts } from "./env"

export function hostOf(url: string): string | null {
  if (!/^https?:/.test(url)) return null
  try {
    return new URL(url).host
  } catch {
    return null
  }
}

export function recordForeignHosts(context: BrowserContext): () => string[] {
  const foreign = new Set<string>()
  context.on("request", (request) => {
    const host = hostOf(request.url())
    if (host && !allowedHosts.includes(host) && !blockedHosts.includes(host)) foreign.add(host)
  })
  return () => [...foreign].sort()
}
