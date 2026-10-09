import { z } from "zod"
import type { ItemKind } from "@/api/items"
import { ISO_DATE_PATTERN, todayIso } from "@/lib/intl/dates"

export const MAX_TITLE = 100
export const MAX_DESCRIPTION = 250
export const MAX_EMAIL = 50
export const MAX_PLACE_NAME = 100
export const MAX_IMAGE_KEY = 200
export const DATE_WITHIN_DAYS = 30
export const MAX_COMPENSATION = 99_999

export { todayIso }

export const ERROR_KEYS: ReadonlySet<string> = new Set([
  "required",
  "tooLong",
  "invalid",
  "future",
  "tooOld",
  "rewardMax",
  "photoRequired",
  "placeRequired",
  "outOfRange",
  "phoneFormat",
  "emailFormat",
  "consent",
  "waiver",
])

export const SOCIAL_MEDIA = ["TELEGRAM", "VIBER", "WHATSAPP"] as const

export const PHONE_PATTERN = /^\+[1-9]\d{7,14}$/

export const consentSchema = z.boolean("consent").refine((value) => value, "consent")

const placeSchema = z.object({
  id: z.string("placeRequired").min(1, "placeRequired").max(255, "tooLong"),
  name: z.string("placeRequired").min(1, "placeRequired").max(MAX_PLACE_NAME, "tooLong"),
  lat: z.number("placeRequired").min(-90, "outOfRange").max(90, "outOfRange"),
  lon: z.number("placeRequired").min(-180, "outOfRange").max(180, "outOfRange"),
  countryCode: z.string("placeRequired").length(2, "placeRequired"),
})

export function reportSchema(kind: ItemKind) {
  return z.object({
    title: z.string("required").trim().min(1, "required").max(MAX_TITLE, "tooLong"),
    description: z.string().trim().max(MAX_DESCRIPTION, "tooLong").optional(),
    date: z
      .string("required")
      .regex(ISO_DATE_PATTERN, "required")
      .refine((value) => value <= todayIso(), "future")
      .refine((value) => value >= todayIso(-DATE_WITHIN_DAYS), "tooOld"),
    compensation: z
      .number()
      .int("invalid")
      .min(0, "invalid")
      .max(MAX_COMPENSATION, "rewardMax")
      .optional(),
    currency: z.string().length(3, "invalid").optional(),
    image:
      kind === "found"
        ? z.string("photoRequired").min(1, "photoRequired").max(MAX_IMAGE_KEY, "tooLong")
        : z.string().max(MAX_IMAGE_KEY, "tooLong").optional(),
    categoryId: z.string("required").min(1, "required"),
    place: placeSchema,
    contact: z.object({
      phone: z.string("phoneFormat").trim().regex(PHONE_PATTERN, "phoneFormat"),
      email: z.email("emailFormat").max(MAX_EMAIL, "tooLong").or(z.literal("")).optional(),
      socialMedias: z.array(z.enum(SOCIAL_MEDIA)).optional(),
    }),
  })
}

export type ReportValues = z.infer<ReturnType<typeof reportSchema>>

export function draftSchema(kind: ItemKind) {
  return reportSchema(kind).omit({ image: true }).extend({ consent: consentSchema })
}

export type ReportDraft = z.infer<ReturnType<typeof draftSchema>>

export function toItemInput(values: ReportValues) {
  return {
    title: values.title,
    description: values.description || null,
    date: values.date,
    compensation:
      values.compensation == null
        ? null
        : { amount: values.compensation, currency: values.currency ?? null },
    image: values.image || null,
    categoryId: values.categoryId,
    place: values.place,
    contact: {
      phone: values.contact.phone,
      email: values.contact.email || null,
      socialMedias: values.contact.socialMedias?.length ? values.contact.socialMedias : null,
    },
  }
}
