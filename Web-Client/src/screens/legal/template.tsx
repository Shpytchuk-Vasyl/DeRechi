import type { Metadata } from "next"
import { getFormatter } from "next-intl/server"
import { Heading, Text } from "@/components/pouf/text"
import {
  LEGAL_CONTACT,
  LEGAL_UPDATED,
  type LegalBlock,
  type LegalKind,
  legalTexts,
} from "@/content/legal"
import { paths } from "@/i18n/paths"
import type { Locale } from "@/i18n/routing"
import { fromIsoDate } from "@/lib/dates"

export function generateMetadataFromTemplate(locale: Locale, kind: LegalKind): Metadata {
  const doc = legalTexts(locale).doc(kind)

  return {
    title: doc.title,
    description: doc.summary,
    alternates: { canonical: `/${locale}${paths[kind]}` },
  }
}

function fill(text: string): string {
  return text
    .replaceAll("{operator}", LEGAL_CONTACT.operator)
    .replaceAll("{email}", LEGAL_CONTACT.email)
}

function Block({ block }: { block: LegalBlock }) {
  if (Array.isArray(block)) {
    return (
      <ul className="list-disc pb-2.5 pl-5">
        {block.map((item) => (
          <li key={item}>
            <Text size="sm" muted>
              {fill(item)}
            </Text>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <Text size="sm" muted>
      {fill(block)}
    </Text>
  )
}

export default async function LegalTemplate({ locale, kind }: { locale: Locale; kind: LegalKind }) {
  const format = await getFormatter()
  const { updated, doc: docOf } = legalTexts(locale)
  const doc = docOf(kind)
  const date = fromIsoDate(LEGAL_UPDATED)

  return (
    <div className="mx-auto max-w-3xl">
      <article className="cushion-card rounded-card bg-surface px-5 pt-[calc(var(--s6)-var(--lip)/2)] pb-[calc(var(--s6)+var(--lip)/2)] sm:px-7 [&>section]:pt-7">
        <header>
          <Heading level={1}>{doc.title}</Heading>
          {date ? (
            <Text size="sm" muted className="mb-2.5 block">
              {updated}: {format.dateTime(date, { dateStyle: "long" })}
            </Text>
          ) : null}
          <Text>{fill(doc.summary)}</Text>
        </header>

        {doc.sections.map((section) => (
          <section key={section.heading}>
            <Heading level={2}>{section.heading}</Heading>
            <div className="leading-relaxed">
              {section.body.map((block) => (
                <Block key={Array.isArray(block) ? block.join("|") : block} block={block} />
              ))}
            </div>
          </section>
        ))}
      </article>
    </div>
  )
}
