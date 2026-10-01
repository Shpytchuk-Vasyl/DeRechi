"use client"

import { useTranslations } from "next-intl"
import { Controller, useFormContext } from "react-hook-form"
import { Checkbox } from "@/components/pouf/checkbox"
import { Field, Input, Label } from "@/components/pouf/Input"
import { useFieldMessage } from "@/hooks/use-field-message"
import { ERROR_KEYS, SOCIAL_MEDIA } from "@/schema/report-schema"
import { Text } from "../pouf/text"

type SocialMedia = (typeof SOCIAL_MEDIA)[number]

type Props = {
  prefix?: "" | "contact."
}

export function ContactFields({ prefix = "" }: Props) {
  const t = useTranslations("form")
  const message = useFieldMessage(ERROR_KEYS)
  const { control } = useFormContext()

  return (
    <>
      <div className="grid gap-6 sm:grid-cols-2">
        <Controller
          control={control}
          name={`${prefix}phone`}
          render={({ field, fieldState }) => (
            <Field label={t("phone")} error={message(fieldState.error?.message)}>
              {(id, describedBy) => (
                <Input
                  ref={field.ref}
                  id={id}
                  describedBy={describedBy}
                  type="tel"
                  name="tel"
                  autoComplete="tel"
                  inputMode="tel"
                  placeholder="+380671234567"
                  value={field.value ?? ""}
                  onChange={(value) => field.onChange(value.replace(/[\s().-]/g, ""))}
                  onBlur={field.onBlur}
                  invalid={Boolean(fieldState.error)}
                />
              )}
            </Field>
          )}
        />

        <Controller
          control={control}
          name={`${prefix}email`}
          render={({ field, fieldState }) => (
            <Field label={t("email")} error={message(fieldState.error?.message)}>
              {(id, describedBy) => (
                <Input
                  ref={field.ref}
                  id={id}
                  describedBy={describedBy}
                  type="email"
                  name="email"
                  autoComplete="email"
                  inputMode="email"
                  value={field.value ?? ""}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  invalid={Boolean(fieldState.error)}
                />
              )}
            </Field>
          )}
        />
      </div>

      <Controller
        control={control}
        name={`${prefix}socialMedias`}
        render={({ field }) => {
          const chosen: SocialMedia[] = field.value ?? []
          return (
            <fieldset className="flex flex-col gap-2">
              <Label as="legend">{t("messengers")}</Label>
              <Text size="sm" muted>
                {t("messengersHint")}
              </Text>
              <div className="mt-1 flex flex-wrap gap-x-6 gap-y-3">
                {SOCIAL_MEDIA.map((channel) => (
                  <Checkbox
                    key={channel}
                    label={t(`social.${channel}`)}
                    checked={chosen.includes(channel)}
                    onChange={(checked) =>
                      field.onChange(
                        checked === true
                          ? [...chosen, channel]
                          : chosen.filter((it) => it !== channel),
                      )
                    }
                  />
                ))}
              </div>
            </fieldset>
          )
        }}
      />
    </>
  )
}
