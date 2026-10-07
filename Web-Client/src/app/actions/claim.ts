"use server"

import { cookies } from "next/headers"
import type { ItemKind } from "@/api/items"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
  FoundItemClaimQuery,
  LostItemClaimQuery,
  UnlockFoundItemClaimMutation,
  UnlockLostItemClaimMutation,
} from "@/graphql/documents"
import { passesBotCheck } from "@/lib/bot-check"
import { CACHE_TAG, invalidate, NO_STORE } from "@/lib/cache"
import { CLAIM_COOKIE_MAX_AGE, claimCookieName } from "@/lib/claim-cookie"
import { CLAIM_ID, claimSchema, toContactInput } from "@/schema/claim-schema"

export type ClaimResult =
  | {
      ok: true
      repeated: boolean
      claimId: string
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
  | { ok: false; reason: "limited"; retryAfter: string | null }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "unavailable" | "failed" }

export type ConfirmResult = { ok: true } | { ok: false; reason: "notFound" | "failed" }

const KINDS: readonly ItemKind[] = ["lost", "found"]

const ITEM_ID = /^[\w-]{1,64}$/

export async function claimNotice(
  kind: ItemKind,
  id: string,
  values: unknown,
): Promise<ClaimResult> {
  try {
    const parsed = claimSchema.safeParse(values)
    if (!isItem(kind, id) || !parsed.success) {
      return { ok: false, reason: "validation" }
    }

    if (!(await passesBotCheck())) {
      return { ok: false, reason: "captcha" }
    }

    const variables = { id, contact: toContactInput(parsed.data) }

    const claim =
      kind === "lost"
        ? (await graphqlRequest(ClaimLostItemMutation, variables, NO_STORE)).claimLostItem
        : (await graphqlRequest(ClaimFoundItemMutation, variables, NO_STORE)).claimFoundItem

    const store = await cookies()
    store.set(claimCookieName(kind, id), claim.id, {
      maxAge: CLAIM_COOKIE_MAX_AGE,
      path: "/",
      sameSite: "lax",
      httpOnly: false,
    })
    return {
      ok: true,
      repeated: claim.repeated,
      claimId: claim.id,
      checkoutUrl: claim.checkoutUrl ?? null,
      paid: claim.paid,
      contactsSent: claim.contactsSent,
    }
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      invalidate(CACHE_TAG.item(kind, id), CACHE_TAG.items(kind))
      return { ok: false, reason: "notFound" }
    }
    console.error("Claim failed", error)
    return { ok: false, reason: "failed" }
  }
}

export async function claimStatus(
  kind: ItemKind,
  itemId: string,
  claimId: string,
): Promise<ClaimStatus> {
  if (!isItem(kind, itemId) || !isClaimId(claimId)) {
    return { ok: false }
  }

  const variables = { itemId, id: claimId }

  try {
    const claim =
      kind === "lost"
        ? (await graphqlRequest(LostItemClaimQuery, variables, NO_STORE)).lostItemClaim
        : (await graphqlRequest(FoundItemClaimQuery, variables, NO_STORE)).foundItemClaim
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

export async function unlockClaim(
  kind: ItemKind,
  itemId: string,
  claimId: string,
): Promise<UnlockResult> {
  if (!isItem(kind, itemId) || !isClaimId(claimId)) {
    return { ok: false, reason: "validation" }
  }

  if (!(await passesBotCheck())) {
    return { ok: false, reason: "captcha" }
  }

  const variables = { itemId, id: claimId }

  try {
    const claim =
      kind === "lost"
        ? (await graphqlRequest(UnlockLostItemClaimMutation, variables, NO_STORE))
            .unlockLostItemClaim
        : (await graphqlRequest(UnlockFoundItemClaimMutation, variables, NO_STORE))
            .unlockFoundItemClaim
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
      const limit = error.unlockLimit
      if (limit) return { ok: false, reason: "limited", retryAfter: limit.retryAfter }
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
    await graphqlRequest(ConfirmReturnMutation, { token }, NO_STORE)
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      return { ok: false, reason: "notFound" }
    }
    console.error("Confirming the return failed", error)
    return { ok: false, reason: "failed" }
  }

  invalidate(...KINDS.map(CACHE_TAG.items))
  return { ok: true }
}

function isItem(kind: unknown, id: unknown): boolean {
  return KINDS.includes(kind as ItemKind) && typeof id === "string" && ITEM_ID.test(id)
}

function isClaimId(value: unknown): value is string {
  return typeof value === "string" && CLAIM_ID.test(value)
}
