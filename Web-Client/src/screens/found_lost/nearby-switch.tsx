"use client"

import { MapPinOff, X } from "lucide-react"
import { useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"
import { useId, useOptimistic, useState, useTransition } from "react"
import { Switch } from "@/components/pouf/controls"
import { Label } from "@/components/pouf/Input"
import { Row } from "@/components/pouf/layout"
import { usePathname, useRouter } from "@/i18n/navigation"
import { type Coordinates, roundCoordinate } from "@/lib/item-search"

type Problem = "denied" | "unavailable"

export default function NearbySwitch({ active }: { active: boolean }) {
  const t = useTranslations("list")
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const id = useId()
  const [locating, setLocating] = useState(false)
  const [problem, setProblem] = useState<Problem | null>(null)
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

  return (
    <>
      <Row gap={2}>
        <Label htmlFor={id}>{locating ? t("nearbyLocating") : t("nearby")}</Label>
        <Switch
          id={id}
          checked={checked || locating}
          disabled={locating || isPending}
          onChange={(next) => (next ? locate() : navigate(undefined))}
        />
      </Row>

      {problem ? (
        <div
          role="status"
          className="cushion-field order-last flex basis-full items-start gap-3 rounded-control bg-bg px-4 py-3 text-sm"
        >
          <MapPinOff className="mt-0.5 size-4 shrink-0" aria-hidden />
          <p className="m-0 flex-1">
            {problem === "denied" ? t("nearbyDenied") : t("nearbyUnavailable")}
          </p>
          <button
            type="button"
            className="-m-1 rounded-pill p-1 text-muted-foreground hover:text-ink"
            aria-label={t("nearbyDismiss")}
            onClick={() => setProblem(null)}
          >
            <X className="size-4" aria-hidden />
          </button>
        </div>
      ) : null}
    </>
  )
}
