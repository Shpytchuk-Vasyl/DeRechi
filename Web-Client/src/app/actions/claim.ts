"use server"

import { cookies } from "next/headers"
import type { ItemKind } from "@/api/items"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
} from "@/graphql/documents"
import { passesBotCheck } from "@/lib/bot-check"
import { CACHE_TAG, invalidate, NO_STORE } from "@/lib/cache"
import { CLAIM_COOKIE_MAX_AGE, claimCookieName } from "@/lib/claim-cookie"
import { claimSchema, toContactInput } from "@/schema/claim-schema"

export type ClaimResult =
  | { ok: true; repeated: boolean; claimId: string }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "disposableEmail" | "failed" }

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
    return { ok: true, repeated: claim.repeated, claimId: claim.id }
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      invalidate(CACHE_TAG.item(kind, id), CACHE_TAG.items(kind))
      return { ok: false, reason: "notFound" }
    }
    if (error instanceof GraphQLRequestError && error.isDisposableEmail) {
      return { ok: false, reason: "disposableEmail" }
    }
    console.error("Claim failed", error)
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
