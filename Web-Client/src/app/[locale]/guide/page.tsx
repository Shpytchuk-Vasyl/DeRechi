import type { Metadata } from "next"
import { routing } from "@/i18n/routing"
import GuideIndexPage, { guideIndexMetadata } from "@/screens/guide/guide-index-page"

type Props = { params: Promise<{ locale: string }> }

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }))
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  return guideIndexMetadata(locale)
}

export default async function GuideIndexRoute({ params }: Props) {
  const { locale } = await params
  return <GuideIndexPage locale={locale} />
}
