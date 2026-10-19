import { defaultLocale, type Locale } from "@/i18n/routing"
import de from "./de.json"
import en from "./en.json"
import fr from "./fr.json"
import pl from "./pl.json"
import { COUNTRIES, PLACES } from "./places"
import type { Country, LostPropertyTexts, NationalTexts, Place, PlaceTexts } from "./types"
import uk from "./uk.json"

export { COUNTRIES, PLACES, UNTRUSTED_HOSTS } from "./places"
export type {
  Country,
  CountryTexts,
  LostPropertyTexts,
  NationalTexts,
  Place,
  PlaceKind,
  PlaceTexts,
  PlaceType,
} from "./types"

const TEXTS = { en, uk, pl, de, fr } satisfies Record<Locale, LostPropertyTexts>

export function lostPropertyTexts(locale: string): LostPropertyTexts {
  return TEXTS[(locale in TEXTS ? locale : defaultLocale) as Locale]
}

export function country(code: string): Country {
  const found = COUNTRIES.find((candidate) => candidate.code === code)
  if (found) return found
  console.warn(`No lost-property advice for country ${code}, showing ${COUNTRIES[0].code}`)
  return COUNTRIES[0]
}

export type CityPlace = Place & PlaceTexts

export type NationalItem = NationalTexts & { key: string; url: string }

export function lostProperty(
  locale: string,
  city: string,
  countryCode: string,
): {
  texts: LostPropertyTexts
  places: CityPlace[]
  national: { title: string; items: NationalItem[] }
  checked: string
} {
  const texts = lostPropertyTexts(locale)
  const places = PLACES.filter((place) => place.city === city).map((place) => ({
    ...place,
    ...texts.places[place.id],
  }))
  const law = country(countryCode)
  const countryTexts = texts.national[law.code]
  const items = Object.entries(law.links).map(([key, url]) => ({
    key,
    url,
    ...countryTexts.items[key],
  }))
  const checked = [law.checked, ...places.map((place) => place.checked)].sort()[0]
  return { texts, places, national: { title: countryTexts.title, items }, checked }
}
