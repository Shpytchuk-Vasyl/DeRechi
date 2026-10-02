import type { ItemKind } from "@/api/items"

export const CLAIM_COOKIE_MAX_AGE = 30 * 24 * 60 * 60

export function claimCookieName(kind: ItemKind, id: string): string {
  return `DERECHI_CLAIM_${kind}_${id}`
}

export function readClaimCookie(cookieHeader: string, kind: ItemKind, id: string): string | null {
  const prefix = `${claimCookieName(kind, id)}=`
  for (const part of cookieHeader.split(";")) {
    const cookie = part.trim()
    if (cookie.startsWith(prefix)) return cookie.slice(prefix.length)
  }
  return null
}

export function hasClaimCookie(cookieHeader: string, kind: ItemKind, id: string): boolean {
  return readClaimCookie(cookieHeader, kind, id) !== null
}
