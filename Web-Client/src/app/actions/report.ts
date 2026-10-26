"use server"

import { ITEM_OPS } from "@/api/item-ops"
import { passesBotCheck } from "@/lib/bot-check"
import { CACHE_TAG, invalidate } from "@/lib/cache"
import type { ItemKind } from "@/lib/kinds"
import { reportSchema, toItemInput } from "@/schema/report-schema"

export type ReportResult =
  | { ok: true; id: string }
  | { ok: false; reason: "validation" | "captcha" | "failed" }

export async function createNotice(kind: ItemKind, values: unknown): Promise<ReportResult> {
  const parsed = reportSchema(kind).safeParse(values)
  try {
    if (!parsed.success) {
      return { ok: false, reason: "validation" }
    }

    if (!(await passesBotCheck())) {
      return { ok: false, reason: "captcha" }
    }

    const created = await ITEM_OPS[kind].create(toItemInput(parsed.data))

    invalidate(CACHE_TAG.items(kind), CACHE_TAG.places)
    if (kind === "found") {
      invalidate(CACHE_TAG.stats)
    }
    return { ok: true, id: created.id }
  } catch (error) {
    console.error("Creating a notice failed", error)
    return { ok: false, reason: "failed" }
  }
}
