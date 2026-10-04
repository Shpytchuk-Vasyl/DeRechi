import { useCallback, useEffect, useState } from "react"
import type { ItemKind } from "@/api/items"
import { type ClaimResult, claimStatus } from "@/app/actions/claim"
import { readClaimCookie } from "@/lib/claim-cookie"
import { CLAIM_ID } from "@/schema/claim-schema"
import type { PhoneUnlock, PhoneUnlockUpdate } from "./claim-unlock"

export type Sent = "new" | "repeated" | null

export type ClaimSuccess = Extract<ClaimResult, { ok: true }>

export function useClaim(kind: ItemKind, itemId: string) {
  const [sent, setSent] = useState<Sent>(null)
  const [unlock, setUnlock] = useState<PhoneUnlock | null>(null)

  useEffect(() => {
    const claimId = readClaimCookie(document.cookie, kind, itemId)
    if (claimId === null) return
    setSent((current) => current ?? "repeated")
    if (!CLAIM_ID.test(claimId)) return

    let active = true
    claimStatus(kind, itemId, claimId)
      .then((status) => {
        if (active && status.ok) {
          setUnlock(
            (current) =>
              current ?? {
                claimId,
                checkoutUrl: status.checkoutUrl,
                paid: status.paid,
                contactsSent: status.contactsSent,
              },
          )
        }
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [kind, itemId])

  const markSent = useCallback((result: ClaimSuccess) => {
    setSent(result.repeated ? "repeated" : "new")
    setUnlock({
      claimId: result.claimId,
      checkoutUrl: result.checkoutUrl,
      paid: result.paid,
      contactsSent: result.contactsSent,
    })
  }, [])

  const updateUnlock = useCallback((update: PhoneUnlockUpdate) => {
    setUnlock((current) => (current ? { ...current, ...update } : current))
  }, [])

  return { sent, unlock, markSent, updateUnlock }
}
