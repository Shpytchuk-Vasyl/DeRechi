import type { Metadata } from "next"
import type { Locale } from "@/i18n/routing"
import LegalTemplate, { generateMetadataFromTemplate } from "@/screens/legal/template"

type Props = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  return generateMetadataFromTemplate(locale as Locale, "terms")
}

export default async function TermsRoute({ params }: Props) {
  const { locale } = await params
  return <LegalTemplate locale={locale as Locale} kind="terms" />
}
