"use client"

import { useTranslations } from "next-intl"
import { Controller, useFormContext, useWatch } from "react-hook-form"
import { useCountry } from "@/components/country/country-provider"
import { ConsentCheckbox } from "@/components/form/consent-checkbox"
import { ContactFields } from "@/components/form/contact-fields"
import { LegalLink } from "@/components/form/legal-link"
import { Select } from "@/components/pouf/controls"
import { Field, Input } from "@/components/pouf/Input"
import { useFieldMessage } from "@/hooks/use-field-message"
import { paths } from "@/i18n/paths"
import { currencies, currencyOf } from "@/lib/intl/country"
import {
  ERROR_KEYS,
  REWARD_KINDS,
  type ReportDraft,
  type RewardKind,
  rewardKindOf,
} from "@/schema/report-schema"

const REWARD_LABELS: Record<RewardKind, "rewardNone" | "rewardNegotiable" | "rewardAmount"> = {
  none: "rewardNone",
  negotiable: "rewardNegotiable",
  amount: "rewardAmount",
}

export default function StepContacts() {
  const t = useTranslations("form")
  const message = useFieldMessage(ERROR_KEYS)
  const viewer = useCountry()
  const { control, setValue } = useFormContext<ReportDraft>()
  const [place, rewardKind, compensation] = useWatch({
    control,
    name: ["place", "rewardKind", "compensation"],
  })

  const placeCountry = place?.countryCode ?? viewer.code
  const placeCurrency = currencyOf(viewer.countries, placeCountry) ?? viewer.currency
  const currencyOptions = currencies(viewer.countries).map((code) => ({ value: code, label: code }))
  const reward = rewardKindOf({ rewardKind, compensation })
  const rewardOptions = REWARD_KINDS.map((kind) => ({ value: kind, label: t(REWARD_LABELS[kind]) }))

  return (
    <>
      <Controller
        control={control}
        name="rewardKind"
        render={({ field }) => (
          <Field label={t("reward")}>
            {(id, describedBy) => (
              <Select
                id={id}
                describedBy={describedBy}
                value={reward}
                onChange={(value) => {
                  field.onChange(value)
                  if (value !== "amount") {
                    setValue("compensation", undefined)
                  }
                }}
                options={rewardOptions}
              />
            )}
          </Field>
        )}
      />

      {reward === "amount" ? (
        <div className="grid gap-6 sm:grid-cols-[minmax(0,1fr)_minmax(0,160px)]">
          <Controller
            control={control}
            name="compensation"
            render={({ field, fieldState }) => (
              <Field label={t("rewardSum")} error={message(fieldState.error?.message)}>
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

          <Controller
            control={control}
            name="currency"
            render={({ field, fieldState }) => (
              <Field label={t("currency")} error={message(fieldState.error?.message)}>
                {(id, describedBy) => (
                  <Select
                    id={id}
                    describedBy={describedBy}
                    value={field.value ?? placeCurrency}
                    onChange={field.onChange}
                    options={currencyOptions}
                  />
                )}
              </Field>
            )}
          />
        </div>
      ) : null}

      <ContactFields prefix="contact." emailOptional />

      <ConsentCheckbox>
        {t.rich("consent", {
          terms: (chunks) => (
            <LegalLink href={{ pathname: paths.terms, query: { country: placeCountry } }}>
              {chunks}
            </LegalLink>
          ),
          privacy: (chunks) => (
            <LegalLink href={{ pathname: paths.privacy, query: { country: placeCountry } }}>
              {chunks}
            </LegalLink>
          ),
        })}
      </ConsentCheckbox>
    </>
  )
}
