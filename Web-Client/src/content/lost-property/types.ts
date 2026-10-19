export type PlaceType = "metro" | "city_transport" | "city_service"

/**
 * `office`: a place that keeps found things and hands them out.
 * `rules`: no such office; the operator's own rules say whom to tell, and that is what we show.
 */
export type PlaceKind = "office" | "rules"

export type Place = {
  id: string
  city: string
  type: PlaceType
  kind: PlaceKind
  phone?: { label: string; tel: string }
  url?: string
  telegram?: string
  checked: string
  sources: string[]
  needsCheck?: boolean
}

export type PlaceTexts = { name: string; text: string }

export type NationalTexts = { title: string; text: string; link: string }

export type LostPropertyTexts = {
  title: string
  lead: string
  leadEmpty: string
  types: Record<PlaceType, string>
  kinds: Record<PlaceKind, string>
  officialPage: string
  telegram: string
  nationalTitle: string
  national: { railway: NationalTexts; bus: NationalTexts; finder: NationalTexts }
  needsCheck: string
  checked: string
  report: string
  places: Record<string, PlaceTexts>
}
