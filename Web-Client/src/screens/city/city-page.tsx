import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import { cityHasNotices } from "@/api/cities"
import { SectionArrival } from "@/components/effects/section-arrival"
import { MascotRoamer } from "@/components/mascot/mascot-roamer"
import { paths } from "@/i18n/paths"
import { cityArea, cityBySlug } from "@/lib/cities"
import { cityPageJsonLd, jsonLd, pageAlternates } from "@/lib/seo"
import LostProperty from "@/screens/city/lost-property"
import ClosingCall from "@/screens/home/closing-call"
import Hero from "@/screens/home/hero"
import QuickSearch from "@/screens/home/quick-search/quick-search"
import RecentRail from "@/screens/home/rail/recent-rail"

export async function cityMetadata(locale: string, slug: string): Promise<Metadata> {
  const city = cityBySlug(slug)
  if (!city) return {}

  const [t, tc] = await Promise.all([
    getTranslations({ locale, namespace: "cityPage" }),
    getTranslations({ locale, namespace: "city" }),
  ])
  const where = tc(`${slug}.in`)
  const title = t("title", { in: where })
  const description = t("description", { in: where })

  return {
    title,
    description,
    alternates: pageAlternates(locale, paths.city(slug)),
    ...((await cityHasNotices(city)) ? {} : { robots: { index: false, follow: true } }),
    openGraph: { type: "website", title, description },
  }
}

export default async function CityPage({ locale, slug }: { locale: string; slug: string }) {
  const city = cityBySlug(slug)
  if (!city) {
    notFound()
  }

  const [t, tc, tn, ta] = await Promise.all([
    getTranslations("cityPage"),
    getTranslations("city"),
    getTranslations("nav"),
    getTranslations("app"),
  ])
  const alternateNames: string[] = []
  for (let i = 0; ta.has(`alternateNames.${i}`); i++) {
    alternateNames.push(ta(`alternateNames.${i}`))
  }
  const where = tc(`${slug}.in`)
  const area = cityArea(city)
  const near = { lat: String(city.lat), lon: String(city.lon) }

  return (
    <>
      <MascotRoamer />

      <div className="flex flex-col gap-6">
        <Hero data-mascot-spot city={slug} />

        <QuickSearch data-mascot-spot near={near} />

        <RecentRail
          kind="found"
          near={area}
          title={t("found", { in: where })}
          seeAll={{ label: t("seeAllFound"), query: near }}
          empty={t("empty")}
        />
        <RecentRail
          kind="lost"
          near={area}
          title={t("lost", { in: where })}
          seeAll={{ label: t("seeAllLost"), query: near }}
          empty={t("empty")}
        />

        <LostProperty locale={locale} slug={slug} countryCode={city.countryCode} where={where} />

        <ClosingCall />
      </div>

      <SectionArrival />

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: jsonLd(
            cityPageJsonLd(
              locale,
              { name: ta("name"), alternateNames, description: ta("description") },
              {
                title: t("title", { in: where }),
                description: t("description", { in: where }),
                home: tn("home"),
              },
              { slug, name: tc(`${slug}.name`), lat: city.lat, lon: city.lon },
            ),
          ),
        }}
      />
    </>
  )
}
