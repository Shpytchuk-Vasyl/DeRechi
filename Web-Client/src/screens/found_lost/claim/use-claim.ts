import { useCallback, useEffect, useState } from "react"
import type { ItemKind } from "@/api/items"
import type { ClaimResult } from "@/app/actions/claim"
import { hasClaimCookie } from "@/lib/claim-cookie"

export type Sent = "new" | "repeated" | null

export type ClaimSuccess = Extract<ClaimResult, { ok: true }>

export function useClaim(kind: ItemKind, itemId: string) {
  const [sent, setSent] = useState<Sent>(null)

  useEffect(() => {
    if (!hasClaimCookie(document.cookie, kind, itemId)) return
    setSent((current) => current ?? "repeated")
  }, [kind, itemId])

  const markSent = useCallback((result: ClaimSuccess) => {
    setSent(result.repeated ? "repeated" : "new")
  }, [])

  return { sent, markSent }
}
