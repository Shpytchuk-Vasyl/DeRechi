import { cx } from "class-variance-authority"
import { Building2, BusFront, ExternalLink, Phone, Scale, Send, TrainFront } from "lucide-react"
import { getFormatter } from "next-intl/server"
import type { ReactElement, ReactNode } from "react"
import { buttonClasses } from "@/components/pouf/Button"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"
import {
  type CityPlace,
  type LostPropertyTexts,
  lostProperty,
  type PlaceType,
} from "@/content/lost-property"
import { paths } from "@/i18n/paths"
import { clientEnv } from "@/lib/env/client"
import { feedbackForm, feedbackUrl } from "@/lib/feedback"

const ICON_TONE: Tone = "blue"

const TYPE_ICON: Record<PlaceType, ReactElement> = {
  metro: <TrainFront />,
  city_transport: <BusFront />,
  city_service: <Building2 />,
}

const NATIONAL_ICON: Record<string, ReactElement> = {
  railway: <TrainFront />,
  bus: <BusFront />,
  finder: <Scale />,
}

type Props = { locale: string; slug: string; countryCode: string; where: string }

export default async function LostProperty({ locale, slug, countryCode, where }: Props) {
  const { texts, places, national, checked } = lostProperty(locale, slug, countryCode)
  const format = await getFormatter({ locale })
  const form = feedbackForm(clientEnv.NEXT_PUBLIC_TALLY_FORM, locale)
  const date = format.dateTime(new Date(checked), { dateStyle: "long" })

  return (
    <Card
      id="lost-property"
      aria-labelledby="lost-property-title"
      tabIndex={-1}
      className="scroll-mt-6"
    >
      <CardHeader>
        <CardTitle id="lost-property-title" level={2}>
          {fill(texts.title, { in: where })}
        </CardTitle>
        <CardDescription>
          {places.length > 0 ? texts.lead : fill(texts.leadEmpty, { in: where })}
        </CardDescription>
      </CardHeader>

      <ul className="flex flex-col">
        {places.map((place) => (
          <Row
            key={place.id}
            icon={TYPE_ICON[place.type]}
            name={place.name}
            meta={
              <>
                {texts.types[place.type]} · <Status place={place} texts={texts} />
              </>
            }
            text={place.text}
          >
            {place.phone ? (
              <Action href={`tel:${place.phone.tel}`} icon={<Phone />}>
                {place.phone.label}
              </Action>
            ) : null}
            {place.url ? (
              <Action href={place.url} icon={<ExternalLink />} external>
                {texts.officialPage}
              </Action>
            ) : null}
            {place.telegram ? (
              <Action href={place.telegram} icon={<Send />} external>
                {texts.telegram}
              </Action>
            ) : null}
          </Row>
        ))}
        {national.items.map((item) => (
          <Row
            key={item.key}
            icon={NATIONAL_ICON[item.key] ?? <Scale />}
            name={item.title}
            meta={national.title}
            text={item.text}
          >
            <Action href={item.url} icon={<ExternalLink />} external>
              {item.link}
            </Action>
          </Row>
        ))}
      </ul>

      <Text size="sm" muted className="block">
        {fill(texts.checked, { date })}
        {form ? (
          <>
            {" "}
            <a
              href={feedbackUrl(form, { page: `${paths.city(slug)}#lost-property` })}
              target="_blank"
              rel="noreferrer"
              className="font-extrabold underline-offset-4 hover:underline"
            >
              {texts.report}
            </a>
          </>
        ) : null}
      </Text>
    </Card>
  )
}

function Row({
  icon,
  name,
  meta,
  text,
  children,
}: {
  icon: ReactElement
  name: string
  meta: ReactNode
  text: string
  children: ReactNode
}) {
  return (
    <li
      data-mascot-spot
      className="flex flex-col gap-3 border-border border-t py-4 first:border-t-0 first:pt-0 md:flex-row md:items-start"
    >
      <Blob icon={icon} tone={ICON_TONE} size="sm" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-extrabold">{name}</span>
          <Text muted size="sm">
            {meta}
          </Text>
        </div>
        <Text size="sm" muted className="mt-1 block leading-relaxed">
          {text}
        </Text>
      </div>
      <div className="flex flex-wrap gap-2 md:w-64 md:flex-none md:flex-col">{children}</div>
    </li>
  )
}

const STATUS_TONE = { office: "mint", rules: "blue", unsure: "warn" } as const satisfies Record<
  string,
  Tone
>

function Status({ place, texts }: { place: CityPlace; texts: LostPropertyTexts }) {
  const tone = place.needsCheck ? STATUS_TONE.unsure : STATUS_TONE[place.kind]
  const label = place.needsCheck ? texts.needsCheck : texts.kinds[place.kind]
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        aria-hidden
        className={cx("inline-block size-2 rounded-full bg-(--tone)", `tone-${tone}`)}
      />
      {label}
    </span>
  )
}

function Action({
  href,
  icon,
  external = false,
  children,
}: {
  href: string
  icon: ReactElement
  external?: boolean
  children: ReactNode
}) {
  return (
    <a
      href={href}
      {...(external ? { target: "_blank", rel: "noreferrer" } : {})}
      className={cx(
        buttonClasses({ size: "sm", variant: "quiet" }),
        "h-auto min-h-10.5 whitespace-normal py-2 text-center no-underline md:w-full [&>svg]:size-4 [&>svg]:flex-none",
      )}
    >
      {icon}
      {children}
    </a>
  )
}

function fill(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => values[key] ?? match)
}
