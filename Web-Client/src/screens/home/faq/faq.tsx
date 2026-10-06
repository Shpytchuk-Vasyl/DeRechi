import { getLocale, getTranslations } from "next-intl/server"
import { Stack } from "@/components/pouf/layout"
import { Heading } from "@/components/pouf/text"
import { DEFAULT_LEGAL_COUNTRY, findersLawByCountry } from "@/content/legal"
import type { Locale } from "@/i18n/routing"
import { jsonLd } from "@/lib/seo"
import FaqAccordion from "./faq-accordion"
import { FAQ_COUNT, FINDERS_LAW_QUESTION } from "./faq-count"

export default async function HomeFaq() {
  const t = await getTranslations("home")
  const locale = (await getLocale()) as Locale
  const findersLaw = findersLawByCountry(locale)

  const defaultLaw = findersLaw[DEFAULT_LEGAL_COUNTRY]
  const questions = Array.from({ length: FAQ_COUNT }, (_, index) => {
    const n = index + 1
    return {
      "@type": "Question",
      name: t(`faqQ${n}`),
      acceptedAnswer: {
        "@type": "Answer",
        text: n === FINDERS_LAW_QUESTION ? defaultLaw : t(`faqA${n}`),
      },
    }
  })

  return (
    <>
      <Stack gap={3}>
        <Heading level={2}>{t("faqTitle")}</Heading>
        <FaqAccordion findersLaw={findersLaw} />
      </Stack>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: jsonLd({
            "@context": "https://schema.org",
            "@type": "FAQPage",
            mainEntity: questions,
          }),
        }}
      />
    </>
  )
}
