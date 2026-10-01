import type { Metadata } from "next"
import { fetchCountries } from "@/api/countries"
import type { Locale } from "@/i18n/routing"
import { currentCountry } from "@/lib/intl/country.server"
import LegalTemplate, { generateMetadataFromTemplate } from "@/screens/legal/template"

type Props = {
  params: Promise<{ locale: string }>
  searchParams: Promise<{ country?: string }>
}

async function resolve({ params, searchParams }: Props) {
  const [{ locale }, { country: override }, countries] = await Promise.all([
    params,
    searchParams,
    fetchCountries(),
  ])
  const country = await currentCountry(countries, override)
  return { locale: locale as Locale, countryCode: country.code }
}

export async function generateMetadata(props: Props): Promise<Metadata> {
  const { locale, countryCode } = await resolve(props)
  return generateMetadataFromTemplate(locale, "terms", countryCode)
}

export default async function TermsRoute(props: Props) {
  const { locale, countryCode } = await resolve(props)
  return <LegalTemplate locale={locale} kind="terms" countryCode={countryCode} />
}
