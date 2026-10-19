import { ArrowRight, CalendarCheck, Clock } from "lucide-react"
import type { Metadata } from "next"
import { getFormatter, getTranslations } from "next-intl/server"
import { cloneElement } from "react"
import { fetchCategories } from "@/api/items"
import { PageHeader } from "@/components/layout/page-header"
import { PageMeta } from "@/components/layout/page-meta"
import { Breadcrumb } from "@/components/pouf/breadcrumb"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/pouf/card"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Eyebrow, Heading, Text } from "@/components/pouf/text"
import {
  GUIDE_META,
  type GuideDoc,
  type GuideSlug,
  type GuideTexts,
  guideTexts,
  isGuideSlug,
} from "@/content/guides"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { fromIsoDate } from "@/lib/intl/dates"
import { readingMinutes } from "@/lib/reading-time"
import { absoluteUrl, jsonLd, pageAlternates, SITE_URL } from "@/lib/seo"
import CityMap from "@/screens/home/city-map/city-map"
import RecentRail from "@/screens/home/rail/recent-rail"
import { Blocks, Steps } from "./guide-blocks"
import GuideFaq from "./guide-faq"
import { GUIDE_ICONS } from "./guide-icons"
import { Row } from "@/components/pouf/layout"

const FAQ_ID = "faq"

// The "where to turn in your city" section of a city page (src/screens/city/lost-property.tsx).
const CITY_LOST_PROPERTY_ANCHOR = "#lost-property"

type Props = { locale: string; slug: GuideSlug }

export function guideMetadata(locale: string, slug: GuideSlug): Metadata {
  const doc = guideTexts(locale).guides[slug]
  return {
    title: doc.title,
    description: doc.description,
    alternates: pageAlternates(locale, paths.guide(slug)),
    openGraph: { type: "article", title: doc.title, description: doc.description },
  }
}

export default async function GuidePage({ locale, slug }: Props) {
  const texts = guideTexts(locale)
  const doc = texts.guides[slug]
  const meta = GUIDE_META[slug]
  const { icon, tone } = GUIDE_ICONS[slug]
  const [tn, ta, format, category] = await Promise.all([
    getTranslations("nav"),
    getTranslations("app"),
    getFormatter(),
    categoryOf(meta.category),
  ])
  const checked = format.dateTime(fromIsoDate(meta.checked) ?? new Date(meta.checked), {
    dateStyle: "long",
  })
  const minutes = readingMinutes(guideWords(doc))
  const contents = [
    ...doc.sections.map((section) => ({ id: section.id, label: section.heading })),
    { id: FAQ_ID, label: texts.faqTitle },
  ]
  const foundInCategory = { category: meta.category }

  return (
    <>
      <Breadcrumb
        items={[
          { label: tn("home"), href: paths.home },
          { label: tn("footerGuides"), href: paths.guides },
          { label: doc.shortTitle },
        ]}
      />

      <section className="mt-5">
        <PageHeader
          title={doc.title}
          description={doc.lead}
          icon={cloneElement(icon, { className: "size-9" })}
          tone={tone}
        />
        <PageMeta
          items={[
            { icon: <Clock />, label: texts.readingTime.replace("{minutes}", String(minutes)) },
            { icon: <CalendarCheck />, label: texts.checked.replace("{date}", checked) },
          ]}
        />
      </section>

      <div className="mt-7 grid gap-10 md:grid-cols-[minmax(0,1fr)_240px]">
        <article className="flex min-w-0 max-w-[70ch] flex-col gap-7">
          <details className="md:hidden">
            <summary className="cursor-pointer font-bold">{texts.contents}</summary>
            <Contents items={contents} />
          </details>

          {doc.sections.map((section) => (
            <section
              key={section.id}
              id={section.id}
              aria-labelledby={`${section.id}-heading`}
              className="flex scroll-mt-6 flex-col gap-4"
            >
              <Heading level={2} id={`${section.id}-heading`} className="whitespace-normal">
                {section.heading}
              </Heading>
              {section.body ? <Blocks blocks={section.body} /> : null}
              {section.steps ? <Steps steps={section.steps} tone={tone} /> : null}
            </section>
          ))}

          <section
            id={FAQ_ID}
            aria-labelledby={`${FAQ_ID}-heading`}
            className="flex scroll-mt-6 flex-col gap-4"
          >
            <Heading level={2} id={`${FAQ_ID}-heading`}>
              {texts.faqTitle}
            </Heading>
            <GuideFaq items={doc.faq} />
          </section>
        </article>

        <aside className="hidden md:block">
          <div className="sticky top-8 flex flex-col [&>a]:mt-5">
            <Eyebrow>{texts.contents}</Eyebrow>
            <Contents items={contents} />
            <LinkButton href={paths.report("lost")} tone="down" block prefetch={false}>
              {texts.ctaLost}
            </LinkButton>
          </div>
        </aside>
      </div>

      <div className="mt-7 flex flex-col gap-6">
        <Card tint="lost" className="lg:grid lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
          <CardHeader>
            <CardTitle level={2}>
              {texts.checkNowTitle}
            </CardTitle>
            <CardDescription>
              {texts.checkNowText}
            </CardDescription>
          </CardHeader>
          <Row gap={3}>
            <LinkButton
              href={`${paths.list("found")}?category=${meta.category}`}
              tone="up"
              prefetch={false}
            >
              {doc.ctaFound}
            </LinkButton>
            <LinkButton href={paths.report("lost")} tone="down" prefetch={false}>
              {texts.ctaLost}
            </LinkButton>
          </Row>
        </Card>

        {category ? (
          <RecentRail
            kind="found"
            categoryId={category.id}
            title={doc.railTitle}
            seeAll={{ label: doc.ctaFound, query: foundInCategory }}
            empty={doc.railEmpty}
          />
        ) : null}

        <CityMap description={texts.cityNote} hash={CITY_LOST_PROPERTY_ANCHOR} />

        <Related slugs={meta.related} texts={texts} />
      </div>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: jsonLd(
            guideJsonLd(locale, slug, doc, {
              site: ta("name"),
              home: tn("home"),
              guides: tn("footerGuides"),
            }),
          ),
        }}
      />
    </>
  )
}

function Contents({ items }: { items: { id: string; label: string }[] }) {
  return (
    <ol className="flex flex-col gap-2 pt-3">
      {items.map((item) => (
        <li key={item.id}>
          <a href={`#${item.id}`} className="text-muted text-sm hover:text-ink">
            {item.label}
          </a>
        </li>
      ))}
    </ol>
  )
}

function Related({ slugs, texts }: { slugs: GuideSlug[]; texts: GuideTexts }) {
  const related = slugs.filter(isGuideSlug)
  if (related.length === 0) return null

  return (
    <section aria-labelledby="related-heading" className="flex flex-col gap-4">
      <Heading level={2} id="related-heading">
        {texts.relatedTitle}
      </Heading>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {related.map((slug) => (
          <li key={slug}>
            <RelatedCard slug={slug} doc={texts.guides[slug]} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function RelatedCard({ slug, doc }: { slug: GuideSlug; doc: GuideDoc }) {
  const { icon, tone } = GUIDE_ICONS[slug]
  return (
    <Link href={paths.guide(slug)} className="block h-full no-underline">
      <Card size="sm" motion="lift" className="h-full">
        <div className="flex items-center gap-3.5">
          <Blob icon={icon} tone={tone} size="sm" />
          <div className="min-w-0 flex-1">
            <Text className="block font-bold">{doc.shortTitle}</Text>
            <Text size="sm" muted className="block">
              {doc.title}
            </Text>
          </div>
          <ArrowRight className="size-4 shrink-0" aria-hidden />
        </div>
      </Card>
    </Link>
  )
}

async function categoryOf(key: string) {
  try {
    return (await fetchCategories()).find((category) => category.key === key) ?? null
  } catch {
    return null
  }
}

function guideWords(doc: GuideDoc): string[] {
  const parts: string[] = [doc.lead]
  for (const section of doc.sections) {
    parts.push(section.heading)
    for (const block of section.body ?? []) {
      if (typeof block === "string") parts.push(block)
      else if (Array.isArray(block)) parts.push(...block)
      else parts.push(block.text)
    }
    for (const step of section.steps ?? []) parts.push(step.title, step.text)
  }
  for (const item of doc.faq) parts.push(item.q, item.a)
  return parts
}

function guideJsonLd(
  locale: string,
  slug: GuideSlug,
  doc: GuideDoc,
  names: { site: string; home: string; guides: string },
) {
  const meta = GUIDE_META[slug]
  const url = absoluteUrl(locale, paths.guide(slug))
  const organization = {
    "@type": "Organization",
    name: names.site,
    url: `${SITE_URL}/`,
    logo: `${SITE_URL}/apple-icon.png`,
  }

  return [
    {
      "@context": "https://schema.org",
      "@type": "Article",
      headline: doc.title,
      description: doc.description,
      inLanguage: locale,
      url,
      mainEntityOfPage: url,
      datePublished: meta.published,
      dateModified: meta.checked,
      author: organization,
      publisher: organization,
    },
    {
      "@context": "https://schema.org",
      "@type": "FAQPage",
      mainEntity: doc.faq.map((item) => ({
        "@type": "Question",
        name: item.q,
        acceptedAnswer: { "@type": "Answer", text: item.a },
      })),
    },
    {
      "@context": "https://schema.org",
      "@type": "BreadcrumbList",
      itemListElement: [
        { "@type": "ListItem", position: 1, name: names.home, item: absoluteUrl(locale) },
        {
          "@type": "ListItem",
          position: 2,
          name: names.guides,
          item: absoluteUrl(locale, paths.guides),
        },
        { "@type": "ListItem", position: 3, name: doc.title, item: url },
      ],
    },
  ]
}
