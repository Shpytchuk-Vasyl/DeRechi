"use server"

import { revalidateTag } from "next/cache"
import type { ItemKind } from "@/api/items"
import { graphqlRequest } from "@/graphql/client"
import { CreateFoundItemMutation, CreateLostItemMutation } from "@/graphql/documents"
import { passesBotCheck } from "@/lib/bot-check"
import { reportSchema, toItemInput } from "@/schema/report-schema"

export type ReportResult =
  | { ok: true; id: string }
  | { ok: false; reason: "validation" | "captcha" | "failed" }

export async function createNotice(kind: ItemKind, values: unknown): Promise<ReportResult> {
  const parsed = reportSchema(kind).safeParse(values)
  if (!parsed.success) {
    return { ok: false, reason: "validation" }
  }

  if (!(await passesBotCheck())) {
    return { ok: false, reason: "captcha" }
  }

  const input = toItemInput(parsed.data)

  try {
    const created =
      kind === "lost"
        ? (await graphqlRequest(CreateLostItemMutation, { input }, { cache: "no-store" }))
            .createLostItem
        : (await graphqlRequest(CreateFoundItemMutation, { input }, { cache: "no-store" }))
            .createFoundItem

    revalidateTag(`items:${kind}`, "max")
    revalidateTag("places", "max")
    return { ok: true, id: created.id }
  } catch (e) {
    console.log(e)
    return { ok: false, reason: "failed" }
  }
}
