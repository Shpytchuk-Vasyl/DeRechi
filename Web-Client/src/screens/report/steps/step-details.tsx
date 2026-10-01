"use client"

import { useTranslations } from "next-intl"
import { Controller, useFormContext } from "react-hook-form"
import type { Category, ItemKind } from "@/api/items"
import { Select } from "@/components/pouf/controls"
import { Field, Input, Textarea } from "@/components/pouf/Input"
import { useFieldMessage } from "@/hooks/use-field-message"
import { ERROR_KEYS, MAX_DESCRIPTION, MAX_TITLE, type ReportDraft } from "@/schema/report-schema"

export default function StepDetails({
  kind,
  categories,
}: {
  kind: ItemKind
  categories: Category[]
}) {
  const t = useTranslations("form")
  const tc = useTranslations("category")
  const message = useFieldMessage(ERROR_KEYS)
  const { control, trigger } = useFormContext<ReportDraft>()

  return (
    <>
      <Controller
        control={control}
        name="title"
        render={({ field, fieldState }) => (
          <Field
            label={kind === "lost" ? t("titleLost") : t("titleFound")}
            error={message(fieldState.error?.message)}
          >
            {(id, describedBy) => (
              <Input
                ref={field.ref}
                id={id}
                describedBy={describedBy}
                value={field.value ?? ""}
                onChange={field.onChange}
                onBlur={field.onBlur}
                maxLength={MAX_TITLE}
                invalid={Boolean(fieldState.error)}
              />
            )}
          </Field>
        )}
      />

      <Controller
        control={control}
        name="categoryId"
        render={({ field, fieldState }) => (
          <Field label={t("category")} error={message(fieldState.error?.message)}>
            {(id, describedBy) => (
              <Select
                id={id}
                describedBy={describedBy}
                value={field.value}
                onChange={(value) => {
                  field.onChange(value)
                  void trigger("categoryId")
                }}
                placeholder={t("categoryPlaceholder")}
                options={categories.map((category) => ({
                  value: category.id,
                  label: tc(category.key),
                }))}
              />
            )}
          </Field>
        )}
      />

      <Controller
        control={control}
        name="description"
        render={({ field, fieldState }) => (
          <Field
            label={t("description")}
            hint={`${t(kind === "lost" ? "descriptionHintLost" : "descriptionHintFound")}. ${(field.value ?? "").length} / ${MAX_DESCRIPTION}`}
            error={message(fieldState.error?.message)}
          >
            {(id, describedBy) => (
              <Textarea
                ref={field.ref}
                id={id}
                describedBy={describedBy}
                value={field.value ?? ""}
                onChange={field.onChange}
                onBlur={field.onBlur}
                rows={4}
                maxLength={MAX_DESCRIPTION}
              />
            )}
          </Field>
        )}
      />
    </>
  )
}
