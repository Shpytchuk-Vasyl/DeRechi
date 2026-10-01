import type { FieldPath } from "react-hook-form"
import type { ReportDraft } from "@/schema/report-schema"

export type StepLabel = "details" | "whereWhen" | "contacts"

export const STEPS: { label: StepLabel; fields: FieldPath<ReportDraft>[] }[] = [
  { label: "details", fields: ["title", "description", "categoryId"] },
  { label: "whereWhen", fields: ["date", "place"] },
  {
    label: "contacts",
    fields: ["compensation", "currency", "contact.phone", "contact.email", "contact.socialMedias"],
  },
]

export const LAST = STEPS.length - 1
