import { getTranslations } from "next-intl/server"
import { Stack } from "@/components/pouf/layout"
import { Heading } from "@/components/pouf/text"
import FaqAccordion from "./faq-accordion"
import { FAQ_COUNT } from "./faq-count"

export default async function HomeFaq() {
  const t = await getTranslations("home")

  const questions = Array.from({ length: FAQ_COUNT }, (_, index) => ({
    "@type": "Question",
    name: t(`faqQ${index + 1}`),
    acceptedAnswer: { "@type": "Answer", text: t(`faqA${index + 1}`) },
  }))

  return (
    <>
      <Stack gap={3}>
        <Heading level={2}>{t("faqTitle")}</Heading>
        <FaqAccordion />
      </Stack>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "FAQPage",
            mainEntity: questions,
          }),
        }}
      />
    </>
  )
}
