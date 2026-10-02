import { z } from "zod"
import { MAX_EMAIL, PHONE_PATTERN, SOCIAL_MEDIA } from "@/schema/report-schema"

export const claimSchema = z.object({
  phone: z.string("phoneFormat").trim().regex(PHONE_PATTERN, "phoneFormat"),
  email: z.email("emailFormat").max(MAX_EMAIL, "tooLong"),
  socialMedias: z.array(z.enum(SOCIAL_MEDIA)).optional(),
})

export const CLAIM_TOKEN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

export type ClaimValues = z.infer<typeof claimSchema>

export function toContactInput(values: ClaimValues) {
  return {
    phone: values.phone,
    email: values.email,
    socialMedias: values.socialMedias?.length ? values.socialMedias : null,
  }
}
