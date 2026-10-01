"use client"

import {
  APILoadingStatus,
  APIProvider,
  useApiLoadingStatus,
  useMapsLibrary,
} from "@vis.gl/react-google-maps"
import { cn } from "cn"
import { LoaderCircle, LocateFixed, MapPin } from "lucide-react"
import { useTranslations } from "next-intl"
import { useEffect, useId, useRef, useState } from "react"
import type { ItemKind } from "@/api/items"
import { searchPlaces } from "@/app/actions/places"
import { Field, inputClasses } from "@/components/pouf/Input"
import { clientEnv } from "@/lib/env/client"
import { MAX_PLACE_NAME } from "@/schema/report-schema"

export type PickedPlace = {
  id: string
  name: string
  lat: number
  lon: number
}

type Props = {
  kind: ItemKind
  value: PickedPlace | null
  onChange: (place: PickedPlace | null) => void
  error?: string
  region?: string
}

type LocateProblem = "denied" | "failed"

const WITH_PIN = "pl-12"
const PIN = "pointer-events-none absolute top-1/2 left-4 size-4.5 -translate-y-1/2 text-muted"

const PLACE_FIELDS = ["place_id", "name", "formatted_address", "geometry"]

declare global {
  interface Window {
    gm_authFailure?: () => void
  }
}

export default function PlacePicker(props: Props) {
  const apiKey = clientEnv.NEXT_PUBLIC_MAPS_API_KEY

  if (!apiKey) {
    return <KnownPlaceSearch {...props} reason="noKey" />
  }

  return (
    <APIProvider apiKey={apiKey}>
      <PlaceAutocomplete {...props} />
    </APIProvider>
  )
}

function PlaceAutocomplete(props: Props) {
  const status = useApiLoadingStatus()
  const places = useMapsLibrary("places")
  const [rejected, setRejected] = useState(false)

  useEffect(() => {
    window.gm_authFailure = () => setRejected(true)
    return () => {
      window.gm_authFailure = undefined
    }
  }, [])

  if (rejected || status === APILoadingStatus.FAILED || status === APILoadingStatus.AUTH_FAILURE) {
    return <KnownPlaceSearch {...props} reason="unavailable" />
  }

  return <Autocomplete {...props} places={places} />
}

function Autocomplete({
  kind,
  value,
  onChange,
  error,
  region = "UA",
  places,
}: Props & { places: google.maps.PlacesLibrary | null }) {
  const t = useTranslations("form")
  const input = useRef<HTMLInputElement>(null)
  const geocoding = useMapsLibrary("geocoding")
  const [locating, setLocating] = useState(false)
  const [problem, setProblem] = useState<LocateProblem | null>(null)

  const latest = useRef(onChange)
  latest.current = onChange

  useEffect(() => {
    if (!places || !input.current) {
      return
    }

    const widget = new places.Autocomplete(input.current, {
      fields: PLACE_FIELDS,
      componentRestrictions: { country: region.toLowerCase() },
    })

    const listener = widget.addListener("place_changed", () => {
      const place = widget.getPlace()
      const location = place.geometry?.location
      if (!location) {
        return
      }

      latest.current({
        id: place.place_id ?? "",
        name: (place.name || place.formatted_address || "").slice(0, MAX_PLACE_NAME),
        lat: location.lat(),
        lon: location.lng(),
      })
    })

    return () => listener.remove()
  }, [places, region])

  function locate() {
    if (!geocoding || !("geolocation" in navigator)) {
      setProblem("failed")
      return
    }

    setLocating(true)
    setProblem(null)
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const { results } = await new geocoding.Geocoder().geocode({
            location: { lat: coords.latitude, lng: coords.longitude },
          })
          const best = results[0]
          if (!best) throw new Error("No address at this position")

          const name = best.formatted_address.slice(0, MAX_PLACE_NAME)
          if (input.current) input.current.value = name
          latest.current({ id: best.place_id, name, lat: coords.latitude, lon: coords.longitude })
        } catch {
          setProblem("failed")
        } finally {
          setLocating(false)
        }
      },
      (failure) => {
        setLocating(false)
        setProblem(failure.code === failure.PERMISSION_DENIED ? "denied" : "failed")
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    )
  }

  return (
    <Field
      label={t("place")}
      hint={t(kind === "lost" ? "placeHintLost" : "placeHintFound")}
      error={error}
    >
      {(id, describedBy) => (
        <>
          <div className="relative">
            <MapPin className={PIN} aria-hidden />
            <input
              id={id}
              ref={input}
              type="text"
              autoComplete="off"
              aria-invalid={Boolean(error) || undefined}
              aria-describedby={describedBy}
              defaultValue={value?.name ?? ""}
              placeholder={t("placePlaceholder")}
              onKeyDown={(event) => {
                if (event.key === "Enter") event.preventDefault()
              }}
              onChange={() => {
                if (value) onChange(null)
              }}
              className={cn(inputClasses({ invalid: Boolean(error) }), WITH_PIN, "pr-14")}
            />
            <button
              type="button"
              onClick={locate}
              disabled={locating || !geocoding}
              title={t("placeLocate")}
              aria-label={locating ? t("placeLocating") : t("placeLocate")}
              className="absolute top-1/2 right-2 grid size-9 -translate-y-1/2 place-items-center rounded-pill text-muted transition-colors hover:bg-bg hover:text-ink disabled:opacity-60"
            >
              {locating ? (
                <LoaderCircle className="size-4.5 animate-spin" aria-hidden />
              ) : (
                <LocateFixed className="size-4.5" aria-hidden />
              )}
            </button>
          </div>
          {problem ? (
            <p role="status" className="m-0 text-muted text-sm">
              {problem === "denied" ? t("placeLocateDenied") : t("placeLocateFailed")}
            </p>
          ) : null}
        </>
      )}
    </Field>
  )
}

const SEARCH_DELAY_MS = 400

function KnownPlaceSearch({
  value,
  onChange,
  error,
  reason,
}: Props & { reason: "noKey" | "unavailable" }) {
  const t = useTranslations("form")
  const listId = useId()
  const [query, setQuery] = useState(value?.name ?? "")
  const [results, setResults] = useState<PickedPlace[]>([])
  const [searching, setSearching] = useState(false)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [locating, setLocating] = useState(false)
  const [problem, setProblem] = useState<LocateProblem | null>(null)
  const latestQuery = useRef("")

  const typed = query.trim()
  const canSearch = typed.length >= 2 && typed !== value?.name

  useEffect(() => {
    if (!canSearch) {
      setResults([])
      setSearching(false)
      return
    }

    setSearching(true)
    latestQuery.current = typed
    const timer = setTimeout(async () => {
      try {
        const found = await searchPlaces(typed)
        if (latestQuery.current === typed) setResults(found)
      } catch {
        if (latestQuery.current === typed) setResults([])
      } finally {
        if (latestQuery.current === typed) setSearching(false)
      }
    }, SEARCH_DELAY_MS)

    return () => clearTimeout(timer)
  }, [typed, canSearch])

  const offerNew = canSearch && !searching && typed.length <= MAX_PLACE_NAME
  const count = results.length + (offerNew ? 1 : 0)
  const showList = open && canSearch && (searching || count > 0)

  function pick(place: PickedPlace) {
    setQuery(place.name)
    setOpen(false)
    setProblem(null)
    onChange(place)
  }

  function addHere() {
    if (!("geolocation" in navigator)) {
      setProblem("failed")
      return
    }

    const name = typed
    setLocating(true)
    setProblem(null)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false)
        const lat = coords.latitude
        const lon = coords.longitude
        pick({ id: `manual:${lat.toFixed(5)},${lon.toFixed(5)}`, name, lat, lon })
      },
      (failure) => {
        setLocating(false)
        setProblem(failure.code === failure.PERMISSION_DENIED ? "denied" : "failed")
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    )
  }

  function choose(index: number) {
    const place = results[index]
    if (place) pick(place)
    else if (offerNew) addHere()
  }

  return (
    <Field
      label={t("place")}
      hint={t(reason === "noKey" ? "placeManual" : "placeUnavailable")}
      error={error}
    >
      {(id, describedBy) => (
        <>
          <div className="relative">
            <MapPin className={PIN} aria-hidden />
            <input
              id={id}
              type="text"
              role="combobox"
              autoComplete="off"
              aria-autocomplete="list"
              aria-expanded={showList}
              aria-controls={listId}
              aria-activedescendant={showList && count > 0 ? `${listId}-${active}` : undefined}
              aria-invalid={Boolean(error) || undefined}
              aria-describedby={describedBy}
              maxLength={MAX_PLACE_NAME}
              value={query}
              placeholder={t("placePlaceholder")}
              onChange={(event) => {
                setQuery(event.target.value)
                setActive(0)
                setOpen(true)
                if (value) onChange(null)
              }}
              onFocus={() => setOpen(true)}
              onBlur={() => setOpen(false)}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown" && count > 0) {
                  event.preventDefault()
                  setOpen(true)
                  setActive((index) => Math.min(index + 1, count - 1))
                } else if (event.key === "ArrowUp" && count > 0) {
                  event.preventDefault()
                  setActive((index) => Math.max(index - 1, 0))
                } else if (event.key === "Escape") {
                  setOpen(false)
                } else if (event.key === "Enter") {
                  event.preventDefault()
                  if (showList && count > 0) choose(active)
                }
              }}
              className={cn(inputClasses({ invalid: Boolean(error) }), WITH_PIN)}
            />

            {showList ? (
              <div
                id={listId}
                role="listbox"
                aria-label={t("place")}
                className="cushion-card absolute inset-x-0 top-full z-20 mt-2 max-h-72 overflow-y-auto rounded-control bg-surface p-1.5"
              >
                {searching && results.length === 0 ? (
                  <div className="flex items-center gap-2 px-3 py-2.5 text-muted-foreground text-sm">
                    <LoaderCircle className="size-4 animate-spin" aria-hidden />
                    {t("placeSearching")}
                  </div>
                ) : null}

                {results.map((place, index) => (
                  <button
                    type="button"
                    key={place.id}
                    id={`${listId}-${index}`}
                    role="option"
                    tabIndex={-1}
                    aria-selected={index === active}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => pick(place)}
                    onMouseEnter={() => setActive(index)}
                    className={cn(
                      "flex w-full cursor-pointer items-center gap-2 rounded-control px-3 py-2.5 text-left text-ink",
                      index === active && "bg-bg",
                    )}
                  >
                    <MapPin className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="truncate">{place.name}</span>
                  </button>
                ))}

                {offerNew ? (
                  <button
                    type="button"
                    id={`${listId}-${results.length}`}
                    role="option"
                    tabIndex={-1}
                    aria-selected={active === results.length}
                    aria-disabled={locating}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => (locating ? undefined : addHere())}
                    onMouseEnter={() => setActive(results.length)}
                    className={cn(
                      "flex w-full cursor-pointer items-start gap-2 rounded-control px-3 py-2.5 text-left text-ink",
                      active === results.length && "bg-bg",
                    )}
                  >
                    {locating ? (
                      <LoaderCircle className="mt-0.5 size-4 shrink-0 animate-spin" aria-hidden />
                    ) : (
                      <LocateFixed
                        className="mt-0.5 size-4 shrink-0 text-muted-foreground"
                        aria-hidden
                      />
                    )}
                    <span className="flex flex-col">
                      <span className="font-bold">{t("placeAddHere", { name: typed })}</span>
                      <span className="text-muted-foreground text-xs">
                        {results.length === 0 ? t("placeNoResults") : t("placeAddHereHint")}
                      </span>
                    </span>
                  </button>
                ) : null}
              </div>
            ) : null}
          </div>
          {problem ? (
            <p role="status" className="m-0 text-muted text-sm">
              {problem === "denied" ? t("placeAddDenied") : t("placeAddFailed")}
            </p>
          ) : null}
        </>
      )}
    </Field>
  )
}
