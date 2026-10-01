"use server"

import { checkBotId } from "botid/server"
import { revalidateTag } from "next/cache"
import type { ItemKind } from "@/api/items"
import { graphqlRequest } from "@/graphql/client"
import { CreateFoundItemMutation, CreateLostItemMutation } from "@/graphql/documents"
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
        ? (await graphqlRequest(CreateLostItemMutation, { input }, { revalidate: false }))
            .createLostItem
        : (await graphqlRequest(CreateFoundItemMutation, { input }, { revalidate: false }))
            .createFoundItem

    revalidateTag(`items:${kind}`, "max")
    revalidateTag("places", "max")
    return { ok: true, id: created.id }
  } catch (e) {
    console.log(e)
    return { ok: false, reason: "failed" }
  }
}

async function passesBotCheck(): Promise<boolean> {
  try {
    const verification = await checkBotId(
      process.env.VERCEL
        ? undefined
        : { developmentOptions: { isDevelopment: true, bypass: "HUMAN" } },
    )
    return !verification.isBot
  } catch (error) {
    console.error("BotID check failed", error)
    return false
  }
}
