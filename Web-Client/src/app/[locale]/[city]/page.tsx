import type { Metadata } from "next"
import { routing } from "@/i18n/routing"
import { CITIES } from "@/lib/cities"
import CityPage, { cityMetadata } from "@/screens/city/city-page"

type Props = { params: Promise<{ locale: string; city: string }> }

export const dynamicParams = false

export function generateStaticParams() {
  return routing.locales.flatMap((locale) => CITIES.map((city) => ({ locale, city: city.slug })))
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, city } = await params
  return cityMetadata(locale, city)
}

export default async function CityRoute({ params }: Props) {
  const { locale, city } = await params
  return <CityPage locale={locale} slug={city} />
}
