"use client"

import { useTranslations } from "next-intl"
import { Accordion } from "@/components/pouf/disclosure"
import { Text } from "@/components/pouf/text"
import { FAQ_COUNT } from "./faq-count"

export default function FaqAccordion() {
  const t = useTranslations("home")

  return (
    <Accordion
      items={Array.from({ length: FAQ_COUNT }, (_, index) => {
        const n = index + 1
        return {
          value: `q${n}`,
          title: t(`faqQ${n}`),
          children: <Text muted>{t(`faqA${n}`)}</Text>,
        }
      })}
    />
  )
}
