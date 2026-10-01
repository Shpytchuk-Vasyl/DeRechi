"use client"

import { useTranslations } from "next-intl"
import { Controller, useFormContext, useWatch } from "react-hook-form"
import type { Category, ItemKind } from "@/api/items"
import { DatePicker } from "@/components/form/date-picker"
import { ImageUpload } from "@/components/items/image-upload"
import { ErrorNote } from "@/components/pouf/feedback"
import { Field, Label } from "@/components/pouf/Input"
import { Stack } from "@/components/pouf/layout"
import { useFieldMessage } from "@/hooks/use-field-message"
import type { Photo } from "@/hooks/use-photo"
import { DATE_WITHIN_DAYS, ERROR_KEYS, type ReportDraft, todayIso } from "@/schema/report-schema"
import PlacePicker from "../place-picker"

type Props = {
  kind: ItemKind
  categories: Category[]
  maxUploadBytes: number
  photo: Photo
}

export default function StepWhereWhen({ kind, categories, maxUploadBytes, photo }: Props) {
  const t = useTranslations("form")
  const message = useFieldMessage(ERROR_KEYS)
  const {
    control,
    trigger,
    setValue,
    resetField,
    formState: { errors },
  } = useFormContext<ReportDraft>()

  const [watchedPlace, categoryId] = useWatch({ control, name: ["place", "categoryId"] })
  const place = watchedPlace ?? null
  const categoryKey = categories.find((category) => category.id === categoryId)?.key

  return (
    <>
      <Stack gap={2}>
        <Label htmlFor="image-upload">{kind === "found" ? t("photoRequired") : t("photo")}</Label>
        <ImageUpload
          file={photo.file}
          onFileChange={photo.change}
          maxBytes={maxUploadBytes}
          uploading={photo.uploading}
          error={photo.error ?? undefined}
        />
        {photo.file && categoryKey === "DOCUMENTS" ? (
          <ErrorNote>{t("photoPrivacy")}</ErrorNote>
        ) : null}
      </Stack>

      <Controller
        control={control}
        name="date"
        render={({ field, fieldState }) => (
          <Field
            label={kind === "lost" ? t("dateLost") : t("dateFound")}
            error={message(fieldState.error?.message)}
          >
            {(id) => (
              <DatePicker
                id={id}
                value={field.value}
                onChange={(value) => {
                  field.onChange(value ?? "")
                  if (value) void trigger("date")
                }}
                placeholder={t("pickDate")}
                min={todayIso(-DATE_WITHIN_DAYS)}
                max={todayIso()}
                invalid={Boolean(fieldState.error)}
              />
            )}
          </Field>
        )}
      />

      <PlacePicker
        kind={kind}
        value={place}
        onChange={(picked) => {
          if (picked) setValue("place", picked, { shouldValidate: true })
          else resetField("place")
        }}
        error={errors.place ? t("error.placeRequired") : undefined}
      />
    </>
  )
}
