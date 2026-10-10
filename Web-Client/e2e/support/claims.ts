import type { BrowserContext, Cookie } from "@playwright/test"
import type { ItemKind } from "./data"
import { env } from "./env"

export const CLAIM_COOKIE_MAX_AGE = 30 * 24 * 60 * 60

export function claimCookieName(kind: ItemKind, itemId: string): string {
  return `DERECHI_CLAIM_${kind}_${itemId}`
}

export async function claimCookie(
  context: BrowserContext,
  kind: ItemKind,
  itemId: string,
): Promise<Cookie | undefined> {
  const name = claimCookieName(kind, itemId)
  return (await context.cookies(env.baseURL)).find((cookie) => cookie.name === name)
}

export function withPlusTag(email: string, tag: string): string {
  const at = email.indexOf("@")
  return `${email.slice(0, at)}+${tag}${email.slice(at)}`
}
