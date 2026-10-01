import { CalendarDays, MapPin } from "lucide-react"
import { useFormatter, useLocale, useTranslations } from "next-intl"
import type { ReactNode } from "react"
import type { ItemKind, ItemSummary } from "@/api/items"
import { ItemPhoto } from "@/components/items/item-photo"
import { MetaRow } from "@/components/items/meta-row"
import { Card } from "@/components/pouf/card"
import { Badge } from "@/components/pouf/media"
import { Skeleton } from "@/components/pouf/skeleton"
import { Heading } from "@/components/pouf/text"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { formatNoticeDate, fromIsoDate, todayIso } from "@/lib/dates"
import { formatHryvnia } from "@/lib/money"
import { Stack } from "../pouf/layout"

const RELATIVE_DAYS = 7

type Props = {
  item: ItemSummary
  kind: ItemKind
}

export function ItemCard({ item, kind }: Props) {
  const t = useTranslations()

  return (
    <Link href={paths.item(kind, item.id)} className="block no-underline">
      <NoticeCard
        title={item.title}
        place={item.place.name}
        date={item.date}
        compensation={item.compensation}
        media={
          <ItemPhoto
            image={item.image}
            alt={t("item.photoOf", { title: item.title })}
            sizes="(max-width: 1024px) 50vw, 240px"
            categoryKey={item.category.key}
            fallbackLabel={t("item.noPhoto")}
          />
        }
      />
    </Link>
  )
}

type NoticeCardProps = {
  title: string
  place: string
  date?: string
  compensation?: number | null
  media: ReactNode
}

export function NoticeCard({ title, place, date, compensation, media }: NoticeCardProps) {
  const t = useTranslations()
  const format = useFormatter()
  const locale = useLocale()

  let when: string | null = null
  const day = fromIsoDate(date)
  if (day) {
    const today = fromIsoDate(todayIso()) ?? new Date()
    const days = Math.round((today.getTime() - day.getTime()) / 86_400_000)
    when =
      days <= RELATIVE_DAYS
        ? new Intl.RelativeTimeFormat(locale, { numeric: "auto" }).format(-days, "day")
        : formatNoticeDate(format, day, today)
  }

  return (
    <Card variant="flush" motion="lift">
      <div className="relative aspect-4/3 overflow-hidden bg-bg">
        {media}

        {compensation ? (
          <Badge
            tone="yellow"
            className="absolute top-2 right-2 text-pretty text-end sm:top-3 sm:right-3"
          >
            {t("item.reward", {
              amount: formatHryvnia(format, compensation),
            })}
          </Badge>
        ) : null}
      </div>

      <Stack gap={1} className="p-3 text-start sm:p-4">
        <Heading className="line-clamp-2 max-sm:text-base" level={3}>
          {title}
        </Heading>
        <MetaRow icon={<MapPin />} truncate>
          {place}
        </MetaRow>
        {when ? (
          <MetaRow icon={<CalendarDays />} faint>
            {when}
          </MetaRow>
        ) : null}
      </Stack>
    </Card>
  )
}

export function ItemCardSkeleton() {
  return (
    <Card variant="flush" className="overflow-hidden">
      <Skeleton className="aspect-4/3 rounded-none" />
      <Stack gap={2} className="p-3 sm:p-4">
        <Skeleton className="h-4.5 w-full" />
        <Skeleton className="h-3.5 w-3/4" />
        <Skeleton className="h-3.5 w-1/2" />
      </Stack>
    </Card>
  )
}
