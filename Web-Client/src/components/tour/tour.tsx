"use client"

import { driver } from "driver.js"
import { useTranslations } from "next-intl"
import { useCallback, useEffect, useRef } from "react"
import { useTour } from "@/components/tour/tour-context"
import "driver.js/dist/driver.css"

export type TourId = "home" | "report"

const STEPS: Record<TourId, string[]> = {
  home: ["report", "search", "recent", "language"],
  report: ["details", "photo", "place", "contacts"],
}

const SEEN_PREFIX = "derechi.tour."

function seen(id: TourId): boolean {
  try {
    return window.localStorage.getItem(SEEN_PREFIX + id) === "done"
  } catch {
    return true
  }
}

function remember(id: TourId) {
  try {
    window.localStorage.setItem(SEEN_PREFIX + id, "done")
  } catch {}
}

export function Tour({ id }: { id: TourId }) {
  const t = useTranslations(`tour.${id}`)
  const common = useTranslations("tour")
  const tour = useTour()

  const run = useCallback(() => {
    const steps = STEPS[id]
      .map((step) => ({
        element: `[data-tour="${step}"]`,
        popover: { title: t(`${step}.title`), description: t(`${step}.text`) },
      }))
      .filter((step) => (document.querySelector(step.element)?.getClientRects().length ?? 0) > 0)

    if (steps.length === 0) {
      return
    }

    driver({
      steps,
      showProgress: true,
      allowClose: true,
      smoothScroll: true,
      stagePadding: 6,
      stageRadius: 12,
      popoverClass: "derechi-tour",
      progressText: common("progress", { current: "{{current}}", total: "{{total}}" }),
      nextBtnText: common("next"),
      prevBtnText: common("back"),
      doneBtnText: common("done"),
      onDestroyed: () => remember(id),
    }).drive()
  }, [id, t, common])

  useEffect(() => {
    return tour?.register(run)
  }, [tour, run])

  const latest = useRef(run)
  latest.current = run

  useEffect(() => {
    if (seen(id)) {
      return
    }

    const timer = setTimeout(() => latest.current(), 600)
    return () => clearTimeout(timer)
  }, [id])

  return null
}
