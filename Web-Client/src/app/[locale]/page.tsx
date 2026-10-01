import { getLocale, getTranslations } from "next-intl/server"
import { MascotRoamer } from "@/components/mascot/mascot-roamer"
import { Tour } from "@/components/tour/tour"
import { absoluteUrl } from "@/lib/seo"
import Benefits from "@/screens/home/benefits/benefits"
import ClosingCall from "@/screens/home/closing-call"
import Explainer from "@/screens/home/explainer/explainer"
import Faq from "@/screens/home/faq/faq"
import Hero from "@/screens/home/hero"
import QuickSearch from "@/screens/home/quick-search/quick-search"
import RecentRail from "@/screens/home/rail/recent-rail"

function websiteJsonLd(locale: string, name: string) {
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name,
    url: absoluteUrl(locale),
    potentialAction: {
      "@type": "SearchAction",
      target: {
        "@type": "EntryPoint",
        urlTemplate: `${absoluteUrl(locale, "/found")}?search={search_term_string}`,
      },
      "query-input": "required name=search_term_string",
    },
  }
}

export default async function HomePage() {
  const locale = await getLocale()
  const t = await getTranslations("app")

  return (
    <>
      <Tour id="home" />
      <MascotRoamer />

      <div className="flex flex-col gap-6">
        <Hero data-mascot-spot />

        <QuickSearch data-mascot-spot />

        <RecentRail kind="found" />
        <RecentRail kind="lost" />

        <Explainer kind="found" />

        <Explainer kind="lost" />

        <Benefits />

        <Faq />

        <ClosingCall />
      </div>

      <script
        type="application/ld+json"
        // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD has no other outlet
        dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteJsonLd(locale, t("name"))) }}
      />
    </>
  )
}
