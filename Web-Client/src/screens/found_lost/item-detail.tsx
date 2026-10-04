import { ExternalLink } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { getFormatter, getLocale, getTranslations } from "next-intl/server"
import { fetchItem, type ItemDetail, type ItemKind } from "@/api/items"
import { ItemPhoto } from "@/components/items/item-photo"
import { Stack } from "@/components/pouf/layout"
import { Badge } from "@/components/pouf/media"
import { Skeleton, Skeletons } from "@/components/pouf/skeleton"
import { Heading, Text } from "@/components/pouf/text"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { absoluteFileUrl } from "@/lib/env/client"
import { countryName } from "@/lib/intl/country"
import { formatNoticeDate, fromIsoDate } from "@/lib/intl/dates"
import { formatMoney } from "@/lib/intl/money"
import { mapsUrl } from "@/lib/maps"
import { absoluteUrl, isStale, pageAlternates, snippet } from "@/lib/seo"
import ClaimCard from "@/screens/found_lost/claim/claim-card"
import PhotoViewer from "@/screens/found_lost/photo-viewer"

type Props = {
  kind: ItemKind
  id: string
  compact?: boolean
}

export default async function ItemDetailPage({ kind, id, compact = false }: Props) {
  const item = await fetchItem(kind, id)
  if (!item) {
    notFound()
  }

  const t = await getTranslations("item")
  const tc = await getTranslations("category")
  const tn = await getTranslations("nav")
  const format = await getFormatter()
  const locale = await getLocale()
  const maps = mapsUrl(item.place.lat, item.place.lon)

  return (
    <>
      {compact ? null : (
        <nav className="text-muted-foreground text-sm">
          <Link href={paths.list(kind)} className="hover:text-foreground">
            {kind === "lost" ? t("lost") : t("found")}
          </Link>
          <span aria-hidden> · </span>
          <span>{item.title}</span>
        </nav>
      )}

      <div
        className={
          compact ? "grid gap-6" : "mt-5 grid gap-10 lg:grid-cols-[minmax(0,520px)_minmax(0,1fr)]"
        }
      >
        <div
          className={`relative overflow-hidden rounded-card bg-bg ${
            compact ? "aspect-video" : "aspect-4/3"
          }`}
        >
          <PhotoViewer
            image={item.image}
            alt={t("photoOf", { title: item.title })}
            openLabel={t("openPhoto")}
            closeLabel={t("closePhoto")}
          >
            <ItemPhoto
              image={item.image}
              alt={t("photoOf", { title: item.title })}
              sizes="(max-width: 1024px) 100vw, 520px"
              preload
              categoryKey={item.category.key}
              fallbackLabel={t("noPhoto")}
              iconClassName="size-24"
            />
          </PhotoViewer>
        </div>

        <div>
          <Heading
            level={1}
            className="wrap-anywhere line-clamp-3 whitespace-normal"
            title={item.title}
          >
            {item.title}
          </Heading>

          <div className="mt-3.5 flex flex-wrap gap-2.5">
            <Badge>{tc(item.category.key)}</Badge>
            {item.compensation && item.compensation.amount > 0 ? (
              <Badge tone="yellow">
                {t("reward", {
                  amount: formatMoney(format, item.compensation),
                })}
              </Badge>
            ) : null}
          </div>

          <dl className="mt-6 grid grid-cols-[90px_1fr] gap-x-4 gap-y-3 text-base">
            <dt className="text-muted-foreground">
              {kind === "lost" ? t("lostOn") : t("foundOn")}
            </dt>
            <dd className="font-medium">
              {formatNoticeDate(format, fromIsoDate(item.date) ?? new Date(item.date))}
            </dd>
            <dt className="text-muted-foreground">{t("place")}</dt>
            <dd className="flex items-center gap-2 font-medium">
              <span>
                {item.place.name}
                <span className="text-muted-foreground">
                  , {countryName(locale, item.place.countryCode)}
                </span>
              </span>
              {maps ? (
                <a
                  href={maps}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={t("openInMaps")}
                  title={t("openInMaps")}
                  className="text-muted-foreground hover:text-ink"
                >
                  <ExternalLink className="size-4" aria-hidden />
                </a>
              ) : null}
            </dd>
          </dl>

          <Heading level={2} className="mt-7 text-xl">
            {t("description")}
          </Heading>
          <Text className="wrap-anywhere mt-2.5 whitespace-pre-line leading-relaxed">
            {item.description || t("noDescription")}
          </Text>

          <ClaimCard
            kind={kind}
            id={item.id}
            countryCode={item.place.countryCode}
            contact={{ phone: item.contact.phone, email: item.contact.email }}
          />
        </div>
      </div>

      {compact ? null : (
        <script
          type="application/ld+json"
          // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
          dangerouslySetInnerHTML={{
            __html: JSON.stringify([
              itemJsonLd(item),
              breadcrumbsJsonLd(locale, kind, item, {
                home: tn("home"),
                list: kind === "lost" ? t("lost") : t("found"),
              }),
            ]),
          }}
        />
      )}
    </>
  )
}

function itemJsonLd(item: ItemDetail) {
  return {
    "@context": "https://schema.org",
    "@type": "ItemPage",
    name: item.title,
    description: item.description ?? undefined,
    datePublished: item.date,
    mainEntity: {
      "@type": "Thing",
      name: item.title,
      description: item.description ?? undefined,
      image: item.image ? absoluteFileUrl(item.image) : undefined,
    },
    contentLocation: {
      "@type": "Place",
      name: item.place.name,
      address: { "@type": "PostalAddress", addressCountry: item.place.countryCode },
      geo:
        item.place.lat != null && item.place.lon != null
          ? {
              "@type": "GeoCoordinates",
              latitude: item.place.lat,
              longitude: item.place.lon,
            }
          : undefined,
    },
  }
}

function breadcrumbsJsonLd(
  locale: string,
  kind: ItemKind,
  item: ItemDetail,
  names: { home: string; list: string },
) {
  const crumbs = [
    { name: names.home, item: absoluteUrl(locale) },
    { name: names.list, item: absoluteUrl(locale, paths.list(kind)) },
    { name: item.title, item: absoluteUrl(locale, paths.item(kind, item.id)) },
  ]

  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: crumbs.map((crumb, index) => ({
      "@type": "ListItem",
      position: index + 1,
      ...crumb,
    })),
  }
}

export async function itemMetadata(kind: ItemKind, locale: string, id: string): Promise<Metadata> {
  const item = await fetchItem(kind, id)
  if (!item) return {}

  const [t, tc, format] = await Promise.all([
    getTranslations({ locale, namespace: "item" }),
    getTranslations({ locale, namespace: "category" }),
    getFormatter({ locale }),
  ])

  const date = format.dateTime(new Date(item.date), { dateStyle: "medium" })
  const title = t("metaTitle", {
    kind: kind === "lost" ? t("lost") : t("found"),
    title: item.title,
    place: item.place.name,
    date,
  })
  const summary = t("metaSummary", {
    category: tc(item.category.key),
    place: item.place.name,
    date,
  })
  const description = snippet(item.description ? `${summary}. ${item.description}` : summary)
  const path = `/${kind}/${id}`

  return {
    title,
    description,
    alternates: pageAlternates(locale, path),
    ...(isStale(item.date) ? { robots: { index: false, follow: true } } : {}),
    openGraph: {
      type: "article",
      title,
      description,
      images: item.image ? [{ url: absoluteFileUrl(item.image), alt: item.title }] : undefined,
    },
  }
}

export function ItemDetailSkeleton() {
  return (
    <div className="grid gap-6" aria-busy="true">
      <Skeleton className="aspect-video rounded-card" />
      <Stack gap={3}>
        <Skeleton className="h-8 w-3/4" />
        <div className="flex gap-2.5">
          <Skeleton className="h-7 w-24 rounded-pill" />
          <Skeleton className="h-7 w-32 rounded-pill" />
        </div>
        <Skeletons variant="text" count={3} />
      </Stack>
      <Skeletons variant="card" />
    </div>
  )
}
