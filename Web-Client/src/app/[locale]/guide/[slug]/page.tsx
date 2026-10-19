import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { GUIDE_SLUGS, isGuideSlug } from "@/content/guides"
import { routing } from "@/i18n/routing"
import GuidePage, { guideMetadata } from "@/screens/guide/guide-page"

type Props = { params: Promise<{ locale: string; slug: string }> }

export const dynamicParams = false

export function generateStaticParams() {
  return routing.locales.flatMap((locale) => GUIDE_SLUGS.map((slug) => ({ locale, slug })))
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug } = await params
  return isGuideSlug(slug) ? guideMetadata(locale, slug) : {}
}

export default async function GuideRoute({ params }: Props) {
  const { locale, slug } = await params
  if (!isGuideSlug(slug)) notFound()

  return <GuidePage locale={locale} slug={slug} />
}
