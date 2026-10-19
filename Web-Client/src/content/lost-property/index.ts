import { defaultLocale, type Locale } from "@/i18n/routing"
import de from "./de.json"
import en from "./en.json"
import fr from "./fr.json"
import pl from "./pl.json"
import { NATIONAL, PLACES } from "./places"
import type { LostPropertyTexts, Place, PlaceTexts } from "./types"
import uk from "./uk.json"

export { NATIONAL, PLACES, UNTRUSTED_HOSTS } from "./places"
export type { LostPropertyTexts, Place, PlaceKind, PlaceTexts, PlaceType } from "./types"

const TEXTS = { en, uk, pl, de, fr } satisfies Record<Locale, LostPropertyTexts>

export function lostPropertyTexts(locale: string): LostPropertyTexts {
  return TEXTS[(locale in TEXTS ? locale : defaultLocale) as Locale]
}

export type CityPlace = Place & PlaceTexts

export function lostProperty(
  locale: string,
  city: string,
): { texts: LostPropertyTexts; places: CityPlace[]; checked: string } {
  const texts = lostPropertyTexts(locale)
  const places = PLACES.filter((place) => place.city === city).map((place) => ({
    ...place,
    ...texts.places[place.id],
  }))
  const checked = [NATIONAL.checked, ...places.map((place) => place.checked)].sort()[0]
  return { texts, places, checked }
}
