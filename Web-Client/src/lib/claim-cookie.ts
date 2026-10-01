import type { ItemKind } from "@/api/items"

export const CLAIM_COOKIE_MAX_AGE = 60 * 60

export function claimCookieName(kind: ItemKind, id: string): string {
  return `DERECHI_CLAIM_${kind}_${id}`
}

export function hasClaimCookie(cookieHeader: string, kind: ItemKind, id: string): boolean {
  const prefix = `${claimCookieName(kind, id)}=`
  return cookieHeader.split(";").some((part) => part.trim().startsWith(prefix))
}
