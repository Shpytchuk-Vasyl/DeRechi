"use client"

import { useFormatter, useTranslations } from "next-intl"
import { useCountry } from "@/components/country/country-provider"
import { formatMoney } from "@/lib/intl/money"

export function SampleReward({ amount }: { amount: number }) {
  const { currency } = useCountry()
  const format = useFormatter()
  const t = useTranslations("item")

  return t("reward", { amount: formatMoney(format, { amount, currency }) })
}

export function SampleAmount({ amount }: { amount: number }) {
  const { currency } = useCountry()
  const format = useFormatter()

  return formatMoney(format, { amount, currency })
}
