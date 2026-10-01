"use server"

import { revalidateTag } from "next/cache"
import { cookies } from "next/headers"
import type { ItemKind } from "@/api/items"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
} from "@/graphql/documents"
import { passesBotCheck } from "@/lib/bot-check"
import { CLAIM_COOKIE_MAX_AGE, claimCookieName } from "@/lib/claim-cookie"
import { claimSchema, toContactInput } from "@/schema/claim-schema"

export type ClaimResult =
  | { ok: true; repeated: boolean }
  | { ok: false; reason: "validation" | "captcha" | "notFound" | "failed" }

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
    store.set(claimCookieName(kind, id), "1", {
      maxAge: CLAIM_COOKIE_MAX_AGE,
      path: "/",
      sameSite: "lax",
      httpOnly: false,
    })
    return { ok: true, repeated: claim.repeated }
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
