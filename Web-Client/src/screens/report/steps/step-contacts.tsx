"use client"

import { useTranslations } from "next-intl"
import type { ReactNode } from "react"
import { Controller, useFormContext } from "react-hook-form"
import { Checkbox } from "@/components/pouf/checkbox"
import { Field, Input, Label } from "@/components/pouf/Input"
import { useFieldMessage } from "@/hooks/use-field-message"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { ERROR_KEYS, type ReportDraft, SOCIAL_MEDIA } from "@/schema/report-schema"

export default function StepContacts() {
  const t = useTranslations("form")
  const message = useFieldMessage(ERROR_KEYS)
  const { control } = useFormContext<ReportDraft>()

  return (
    <>
      <Controller
        control={control}
        name="compensation"
        render={({ field, fieldState }) => (
          <Field label={t("reward")} error={message(fieldState.error?.message)}>
            {(id, describedBy) => (
              <Input
                ref={field.ref}
                id={id}
                describedBy={describedBy}
                type="text"
                inputMode="numeric"
                autoComplete="off"
                value={field.value === undefined ? "" : String(field.value)}
                onChange={(value) => {
                  const digits = value.replace(/\D/g, "")
                  field.onChange(digits === "" ? undefined : Number(digits))
                }}
                onBlur={field.onBlur}
                invalid={Boolean(fieldState.error)}
              />
            )}
          </Field>
        )}
      />

      <div className="grid gap-6 sm:grid-cols-2">
        <Controller
          control={control}
          name="contact.phone"
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
          name="contact.email"
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
        name="contact.socialMedias"
        render={({ field }) => {
          const chosen = field.value ?? []
          return (
            <fieldset className="flex flex-col gap-2">
              <Label as="legend">{t("messengers")}</Label>
              <p className="font-bold text-[13px] text-muted-foreground">{t("messengersHint")}</p>
              <div className="mt-1 flex flex-wrap gap-x-6 gap-y-3">
                {SOCIAL_MEDIA.map((channel) => (
                  <Checkbox
                    key={channel}
                    id={`channel-${channel}`}
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

      <p className="text-muted-foreground text-sm">
        {t.rich("consent", {
          terms: (chunks) => <LegalLink href={paths.terms}>{chunks}</LegalLink>,
          privacy: (chunks) => <LegalLink href={paths.privacy}>{chunks}</LegalLink>,
        })}
      </p>
    </>
  )
}

function LegalLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} target="_blank" rel="noopener" className="font-bold underline">
      {children}
    </Link>
  )
}
