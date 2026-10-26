"use server"

import { cookies } from "next/headers"
import { ITEM_OPS } from "@/api/item-ops"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import { ConfirmReturnMutation } from "@/graphql/documents"
import { requestFailure } from "@/graphql/failure"
import { passesBotCheck } from "@/lib/bot-check"
import { CACHE_TAG, invalidate, NO_STORE } from "@/lib/cache"
import { CLAIM_COOKIE_MAX_AGE, claimCookieName } from "@/lib/claim-cookie"
import { ITEM_KINDS, type ItemKind, isItemKind } from "@/lib/kinds"
import { type UnreachableAuthor, unreachableAuthor } from "@/lib/unreachable-author"
import { claimSchema, toContactInput } from "@/schema/claim-schema"

export type ClaimResult =
  | { ok: true; repeated: boolean; claimId: string }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "disposableEmail" | "failed" }
  | { ok: false; reason: "authorUnreachable"; author: UnreachableAuthor }

export type ConfirmResult = { ok: true } | { ok: false; reason: "notFound" | "failed" }

const ITEM_ID = /^[\w-]{1,64}$/

export async function claimNotice(
  kind: ItemKind,
  id: string,
  values: unknown,
): Promise<ClaimResult> {
  try {
    const parsed = claimSchema.safeParse(values)
    if (!isItemKind(kind) || !isItemId(id) || !parsed.success) {
      return { ok: false, reason: "validation" }
    }

    if (!(await passesBotCheck())) {
      return { ok: false, reason: "captcha" }
    }

    const claim = await ITEM_OPS[kind].claim(id, toContactInput(parsed.data))

    const store = await cookies()
    store.set(claimCookieName(kind, id), claim.id, {
      maxAge: CLAIM_COOKIE_MAX_AGE,
      path: "/",
      sameSite: "lax",
      httpOnly: false,
    })
    return { ok: true, repeated: claim.repeated, claimId: claim.id }
  } catch (error) {
    const failure = requestFailure(error)
    if (failure === "notFound") {
      invalidate(CACHE_TAG.item(kind, id), CACHE_TAG.items(kind))
    }
    if (failure !== "failed") {
      return { ok: false, reason: failure }
    }
    const author = error instanceof GraphQLRequestError ? unreachableAuthor(error.errors) : null
    if (author) {
      return { ok: false, reason: "authorUnreachable", author }
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
    if (requestFailure(error) === "notFound") {
      return { ok: false, reason: "notFound" }
    }
    console.error("Confirming the return failed", error)
    return { ok: false, reason: "failed" }
  }

  invalidate(...ITEM_KINDS.map(CACHE_TAG.items))
  return { ok: true }
}

function isItemId(id: unknown): id is string {
  return typeof id === "string" && ITEM_ID.test(id)
}
