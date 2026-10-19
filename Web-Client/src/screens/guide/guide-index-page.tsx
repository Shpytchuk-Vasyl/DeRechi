import { ArrowRight, BookOpen } from "lucide-react"
import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { PageHeader } from "@/components/layout/page-header"
import { Card } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { GUIDE_SLUGS, guideTexts } from "@/content/guides"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { absoluteUrl, jsonLd, pageAlternates } from "@/lib/seo"
import { GUIDE_ICONS } from "./guide-icons"

export function guideIndexMetadata(locale: string): Metadata {
  const { index } = guideTexts(locale)
  return {
    title: index.title,
    description: index.description,
    alternates: pageAlternates(locale, paths.guides),
    openGraph: { type: "website", title: index.title, description: index.description },
  }
}

export default async function GuideIndexPage({ locale }: { locale: string }) {
  const texts = guideTexts(locale)
  const tn = await getTranslations("nav")

  return (
    <>
      <PageHeader
        title={texts.index.title}
        description={texts.index.lead}
        icon={<BookOpen className="size-9" />}
        tone="purple"
      />

      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {GUIDE_SLUGS.map((slug) => {
          const doc = texts.guides[slug]
          const { icon, tone } = GUIDE_ICONS[slug]
          return (
            <li key={slug}>
              <Link href={paths.guide(slug)} className="block h-full no-underline">
                <Card motion="lift" className="h-full">
                  <Blob icon={icon} tone={tone} size="sm" />
                  <Heading level={2} className="whitespace-normal text-lg">
                    {doc.title}
                  </Heading>
                  <Text muted className="leading-relaxed">
                    {doc.description}
                  </Text>
                  <span className="mt-auto inline-flex items-center gap-1.5 font-extrabold">
                    {texts.index.read}
                    <ArrowRight className="size-4" aria-hidden />
                  </span>
                </Card>
              </Link>
            </li>
          )
        })}
      </ul>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: jsonLd([
            {
              "@context": "https://schema.org",
              "@type": "CollectionPage",
              name: texts.index.title,
              description: texts.index.description,
              inLanguage: locale,
              url: absoluteUrl(locale, paths.guides),
              hasPart: GUIDE_SLUGS.map((slug) => ({
                "@type": "Article",
                headline: texts.guides[slug].title,
                url: absoluteUrl(locale, paths.guide(slug)),
              })),
            },
            {
              "@context": "https://schema.org",
              "@type": "BreadcrumbList",
              itemListElement: [
                { "@type": "ListItem", position: 1, name: tn("home"), item: absoluteUrl(locale) },
                {
                  "@type": "ListItem",
                  position: 2,
                  name: tn("footerGuides"),
                  item: absoluteUrl(locale, paths.guides),
                },
              ],
            },
          ]),
        }}
      />
    </>
  )
}
