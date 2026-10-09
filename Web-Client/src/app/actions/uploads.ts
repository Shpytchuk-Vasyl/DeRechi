"use server"

import * as z from "zod"
import {
  ALLOWED_IMAGE_TYPES,
  createUploadTarget,
  MAX_UPLOAD_BYTES,
  type UploadTarget,
} from "@/lib/uploads/storage"

const requestSchema = z.object({
  contentType: z.string(),
  size: z.number().int().positive(),
})

export type UploadTicket =
  | ({ ok: true } & UploadTarget)
  | { ok: false; reason: "unsupportedType" | "tooLarge" }

export async function createUploadTicket(input: unknown): Promise<UploadTicket> {
  const parsed = requestSchema.safeParse(input)

  if (!parsed.success || !ALLOWED_IMAGE_TYPES[parsed.data.contentType]) {
    return { ok: false, reason: "unsupportedType" }
  }

  if (parsed.data.size > MAX_UPLOAD_BYTES) {
    return { ok: false, reason: "tooLarge" }
  }

  return { ok: true, ...createUploadTarget(parsed.data.contentType, parsed.data.size) }
}
