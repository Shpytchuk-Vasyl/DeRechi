import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { paths } from "@/i18n/paths"
import { pageAlternates } from "@/lib/seo"
import SafetyPage from "@/screens/safety/safety-page"

type Props = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "safety" })
  return {
    title: t("title"),
    description: t("metaDescription"),
    alternates: pageAlternates(locale, paths.safety),
  }
}

export default function SafetyRoute() {
  return <SafetyPage />
}
