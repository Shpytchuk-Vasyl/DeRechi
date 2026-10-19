import { CalendarCheck, Clock, Lock, ScrollText } from "lucide-react"
import type { Metadata } from "next"
import { getFormatter } from "next-intl/server"
import { PageHeader } from "@/components/layout/page-header"
import { PageMeta } from "@/components/layout/page-meta"
import { Heading, Text } from "@/components/pouf/text"
import {
  type JurisdictionTexts,
  LEGAL_CONTACT,
  type LegalBlock,
  type LegalDoc,
  type LegalKind,
  legalTexts,
} from "@/content/legal"
import { paths } from "@/i18n/paths"
import type { Locale } from "@/i18n/routing"
import { fromIsoDate } from "@/lib/intl/dates"
import { readingMinutes } from "@/lib/reading-time"
import { pageAlternates } from "@/lib/seo"

const HEADER = {
  terms: { icon: <ScrollText className="size-9" />, tone: "blue" },
  privacy: { icon: <Lock className="size-9" />, tone: "mint" },
} as const

export function generateMetadataFromTemplate(
  locale: Locale,
  kind: LegalKind,
  countryCode: string,
): Metadata {
  const { doc: docOf, slots } = legalTexts(locale, countryCode)
  const doc = docOf(kind)

  return {
    title: doc.title,
    description: fill(doc.summary, slots),
    alternates: pageAlternates(locale, paths[kind]),
  }
}

function fill(text: string, slots: JurisdictionTexts): string {
  let filled = text
  for (const [key, value] of Object.entries(slots)) {
    filled = filled.replaceAll(`{${key}}`, value)
  }
  return filled
    .replaceAll("{operator}", LEGAL_CONTACT.operator)
    .replaceAll("{email}", LEGAL_CONTACT.email)
}

function legalWords(doc: LegalDoc, slots: JurisdictionTexts): string[] {
  const parts = [doc.summary]
  for (const section of doc.sections) {
    parts.push(section.heading)
    for (const block of section.body) parts.push(...(Array.isArray(block) ? block : [block]))
  }
  return parts.map((text) => fill(text, slots))
}

function Block({ block, slots }: { block: LegalBlock; slots: JurisdictionTexts }) {
  if (Array.isArray(block)) {
    return (
      <ul className="list-disc pb-2.5 pl-5">
        {block.map((item) => (
          <li key={item}>
            <Text size="sm" muted>
              {fill(item, slots)}
            </Text>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <Text size="sm" muted className="block">
      {fill(block, slots)}
    </Text>
  )
}

export default async function LegalTemplate({
  locale,
  kind,
  countryCode,
}: {
  locale: Locale
  kind: LegalKind
  countryCode: string
}) {
  const format = await getFormatter()
  const { updated, readingTime, updatedOn, slots, doc: docOf } = legalTexts(locale, countryCode)
  const doc = docOf(kind)
  const date = fromIsoDate(updatedOn)
  const minutes = readingMinutes(legalWords(doc, slots))

  return (
    <>
      <PageHeader
        title={doc.title}
        description={fill(doc.summary, slots)}
        icon={HEADER[kind].icon}
        tone={HEADER[kind].tone}
      />
      <PageMeta
        items={[
          { icon: <Clock />, label: readingTime.replace("{minutes}", String(minutes)) },
          ...(date
            ? [
                {
                  icon: <CalendarCheck />,
                  label: `${updated}: ${format.dateTime(date, { dateStyle: "long" })}`,
                },
              ]
            : []),
        ]}
      />
      <div className="mx-auto max-w-3xl">
        <article className="cushion-card mt-7 rounded-card bg-surface px-5 pt-[calc(var(--s6)-var(--lip)/2)] pb-[calc(var(--s6)+var(--lip)/2)] sm:px-7 [&>section:not(:first-child)]:pt-7">
          {doc.sections.map((section) => (
            <section key={section.heading} id={section.id}>
              <Heading level={2}>{section.heading}</Heading>
              <div className="leading-relaxed">
                {section.body.map((block) => (
                  <Block
                    key={Array.isArray(block) ? block.join("|") : block}
                    block={block}
                    slots={slots}
                  />
                ))}
              </div>
            </section>
          ))}
        </article>
      </div>
    </>
  )
}
