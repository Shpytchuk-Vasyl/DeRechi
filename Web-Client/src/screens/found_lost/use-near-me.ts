"use client"

import { useSearchParams } from "next/navigation"
import { useOptimistic, useState, useTransition } from "react"
import { usePathname, useRouter } from "@/i18n/navigation"
import { type Coordinates, roundCoordinate } from "@/lib/item-search"

export type NearProblem = "denied" | "unavailable"

export function useNearMe(active: boolean) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const [locating, setLocating] = useState(false)
  const [problem, setProblem] = useState<NearProblem | null>(null)
  const [isPending, startTransition] = useTransition()
  const [checked, setChecked] = useOptimistic(active)

  function navigate(near?: Coordinates) {
    const query = new URLSearchParams(params.toString())
    if (near) {
      query.set("lat", String(near.lat))
      query.set("lon", String(near.lon))
    } else {
      query.delete("lat")
      query.delete("lon")
    }

    const search = query.toString()
    startTransition(() => {
      setChecked(Boolean(near))
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false })
    })
  }

  function locate() {
    if (!("geolocation" in navigator)) {
      setProblem("unavailable")
      return
    }

    setLocating(true)
    setProblem(null)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false)
        navigate({ lat: roundCoordinate(coords.latitude), lon: roundCoordinate(coords.longitude) })
      },
      (error) => {
        setLocating(false)
        setProblem(error.code === error.PERMISSION_DENIED ? "denied" : "unavailable")
      },
      { maximumAge: 5 * 60_000, timeout: 15_000 },
    )
  }

  return {
    checked,
    locating,
    isPending,
    problem,
    locate,
    turnOff: () => navigate(undefined),
    dismissProblem: () => setProblem(null),
  }
}
