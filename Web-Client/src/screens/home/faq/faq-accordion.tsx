"use client"

import { useTranslations } from "next-intl"
import { useCountry } from "@/components/country/country-provider"
import { Accordion } from "@/components/pouf/disclosure"
import { Text } from "@/components/pouf/text"
import { FAQ_COUNT, FINDERS_LAW_QUESTION } from "./faq-count"

type Props = {
  findersLaw: Record<string, string>
}

export default function FaqAccordion({ findersLaw }: Props) {
  const t = useTranslations("home")
  const { code } = useCountry()

  return (
    <Accordion
      items={Array.from({ length: FAQ_COUNT }, (_, index) => {
        const n = index + 1
        const answer = n === FINDERS_LAW_QUESTION ? findersLaw[code] : undefined
        return {
          value: `q${n}`,
          title: t(`faqQ${n}`),
          children: <Text muted>{answer ?? t(`faqA${n}`)}</Text>,
        }
      })}
    />
  )
}
