"use server"

import { revalidateTag } from "next/cache"
import { cookies } from "next/headers"
import type { ItemKind } from "@/api/items"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import {
  ClaimByTokenQuery,
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
  UnlockClaimMutation,
} from "@/graphql/documents"
import { passesBotCheck } from "@/lib/bot-check"
import { CLAIM_COOKIE_MAX_AGE, claimCookieName } from "@/lib/claim-cookie"
import { CLAIM_TOKEN, claimSchema, toContactInput } from "@/schema/claim-schema"

export type ClaimResult =
  | {
      ok: true
      repeated: boolean
      token: string
      checkoutUrl: string | null
      paid: boolean
      contactsSent: boolean
    }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "failed" }

export type ClaimStatus =
  | { ok: true; checkoutUrl: string | null; paid: boolean; contactsSent: boolean }
  | { ok: false }

export type UnlockResult =
  | { ok: true; checkoutUrl: string; paid: boolean; contactsSent: boolean }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "unavailable" | "failed" }

export type ConfirmResult = { ok: true } | { ok: false; reason: "notFound" | "failed" }

const KINDS: readonly ItemKind[] = ["lost", "found"]

const ITEM_ID = /^[\w-]{1,64}$/

export async function claimNotice(
  kind: ItemKind,
  id: string,
  values: unknown,
): Promise<ClaimResult> {
  const parsed = claimSchema.safeParse(values)
  if (!KINDS.includes(kind) || typeof id !== "string" || !ITEM_ID.test(id) || !parsed.success) {
    return { ok: false, reason: "validation" }
  }

  if (!(await passesBotCheck())) {
    return { ok: false, reason: "captcha" }
  }

  const variables = { id, contact: toContactInput(parsed.data) }

  try {
    const claim =
      kind === "lost"
        ? (await graphqlRequest(ClaimLostItemMutation, variables, { revalidate: false }))
            .claimLostItem
        : (await graphqlRequest(ClaimFoundItemMutation, variables, { revalidate: false }))
            .claimFoundItem

    const store = await cookies()
    store.set(claimCookieName(kind, id), claim.token, {
      maxAge: CLAIM_COOKIE_MAX_AGE,
      path: "/",
      sameSite: "lax",
      httpOnly: false,
    })
    return {
      ok: true,
      repeated: claim.repeated,
      token: claim.token,
      checkoutUrl: claim.checkoutUrl ?? null,
      paid: claim.paid,
      contactsSent: claim.contactsSent,
    }
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      revalidateTag(`item:${kind}:${id}`, "max")
      revalidateTag(`items:${kind}`, "max")
      return { ok: false, reason: "notFound" }
    }
    console.error("Claim failed", error)
    return { ok: false, reason: "failed" }
  }
}

export async function claimStatus(token: string): Promise<ClaimStatus> {
  if (!isToken(token)) {
    return { ok: false }
  }

  try {
    const { claim } = await graphqlRequest(ClaimByTokenQuery, { token }, { revalidate: false })
    return claim
      ? {
          ok: true,
          checkoutUrl: claim.checkoutUrl ?? null,
          paid: claim.paid,
          contactsSent: claim.contactsSent,
        }
      : { ok: false }
  } catch (error) {
    console.error("Reading the claim status failed", error)
    return { ok: false }
  }
}

export async function unlockClaim(token: string): Promise<UnlockResult> {
  if (!isToken(token)) {
    return { ok: false, reason: "validation" }
  }

  if (!(await passesBotCheck())) {
    return { ok: false, reason: "captcha" }
  }

  try {
    const { unlockClaim: claim } = await graphqlRequest(
      UnlockClaimMutation,
      { token },
      { revalidate: false },
    )
    if (!claim.checkoutUrl) {
      return { ok: false, reason: "failed" }
    }
    return {
      ok: true,
      checkoutUrl: claim.checkoutUrl,
      paid: claim.paid,
      contactsSent: claim.contactsSent,
    }
  } catch (error) {
    if (error instanceof GraphQLRequestError) {
      if (error.isNotFound) return { ok: false, reason: "notFound" }
      if (error.isPaymentUnavailable) return { ok: false, reason: "unavailable" }
    }
    console.error("Preparing the payment failed", error)
    return { ok: false, reason: "failed" }
  }
}

export async function confirmReturn(token: string): Promise<ConfirmResult> {
  if (typeof token !== "string" || token.length === 0 || token.length > 64) {
    return { ok: false, reason: "notFound" }
  }

  try {
    await graphqlRequest(ConfirmReturnMutation, { token }, { revalidate: false })
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      return { ok: false, reason: "notFound" }
    }
    console.error("Confirming the return failed", error)
    return { ok: false, reason: "failed" }
  }

  for (const kind of KINDS) {
    revalidateTag(`items:${kind}`, "max")
  }
  return { ok: true }
}

function isToken(value: unknown): value is string {
  return typeof value === "string" && CLAIM_TOKEN.test(value)
}
