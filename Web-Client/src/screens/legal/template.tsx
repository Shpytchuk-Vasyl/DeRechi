import type { Metadata } from "next"
import { getFormatter, getTranslations } from "next-intl/server"
import { CountrySwitcher } from "@/components/layout/country-switcher"
import { Heading, Text } from "@/components/pouf/text"
import {
  type JurisdictionTexts,
  LEGAL_CONTACT,
  type LegalBlock,
  type LegalKind,
  legalTexts,
} from "@/content/legal"
import { paths } from "@/i18n/paths"
import type { Locale } from "@/i18n/routing"
import { countryName } from "@/lib/country"
import { fromIsoDate } from "@/lib/dates"

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
    alternates: { canonical: `/${locale}${paths[kind]}` },
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
    <Text size="sm" muted>
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
  const t = await getTranslations("nav")
  const { updated, updatedOn, country, slots, doc: docOf } = legalTexts(locale, countryCode)
  const doc = docOf(kind)
  const date = fromIsoDate(updatedOn)

  return (
    <div className="mx-auto max-w-3xl">
      <article className="cushion-card rounded-card bg-surface px-5 pt-[calc(var(--s6)-var(--lip)/2)] pb-[calc(var(--s6)+var(--lip)/2)] sm:px-7 [&>section]:pt-7">
        <header>
          <Heading level={1}>{doc.title}</Heading>
          {/* <div className="mb-2.5 flex flex-wrap items-center gap-x-4 gap-y-1">
            <Text size="sm" muted>
              {t("legalCountry", { country: countryName(locale, country) })}
            </Text>
            <CountrySwitcher />
            {date ? (
              <Text size="sm" muted>
                {updated}: {format.dateTime(date, { dateStyle: "long" })}
              </Text>
            ) : null}
          </div> */}
          {date ? (
            <Text size="sm" muted className="mb-2.5 block">
              {updated}: {format.dateTime(date, { dateStyle: "long" })}
            </Text>
          ) : null}
          <Text>{fill(doc.summary, slots)}</Text>
        </header>

        {doc.sections.map((section) => (
          <section key={section.heading}>
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
  )
}
