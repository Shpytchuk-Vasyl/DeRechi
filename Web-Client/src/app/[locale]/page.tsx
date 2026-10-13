import type { Metadata } from "next"
import { getLocale, getTranslations } from "next-intl/server"
import { MascotRoamer } from "@/components/mascot/mascot-roamer"
import { jsonLd, pageAlternates, websiteJsonLd } from "@/lib/seo"
import Benefits from "@/screens/home/benefits/benefits"
import CityMap from "@/screens/home/city-map/city-map"
import ClosingCall from "@/screens/home/closing-call"
import Explainer from "@/screens/home/explainer/explainer"
import Faq from "@/screens/home/faq/faq"
import Hero from "@/screens/home/hero"
import QuickSearch from "@/screens/home/quick-search/quick-search"
import RecentRail from "@/screens/home/rail/recent-rail"

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>
}): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "app" })
  const title = t("homeTitle")

  return {
    title: { absolute: title },
    alternates: pageAlternates(locale, ""),
    openGraph: {
      type: "website",
      siteName: t("name"),
      locale,
      title,
      description: t("description"),
    },
  }
}

export default async function HomePage() {
  const locale = await getLocale()
  const t = await getTranslations("app")
  const alternateNames: string[] = []
  for (let i = 0; t.has(`alternateNames.${i}`); i++) {
    alternateNames.push(t(`alternateNames.${i}`))
  }

  return (
    <>
      <MascotRoamer />

      <div className="flex flex-col gap-6">
        <Hero data-mascot-spot />

        <QuickSearch data-mascot-spot />

        <RecentRail kind="found" />
        <RecentRail kind="lost" />

        <CityMap />

        <Explainer kind="found" />

        <Explainer kind="lost" />

        <Benefits />

        <Faq />

        <ClosingCall />
      </div>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{
          __html: jsonLd(
            websiteJsonLd(locale, {
              name: t("name"),
              alternateNames,
              description: t("description"),
            }),
          ),
        }}
      />
    </>
  )
}
