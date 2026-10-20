"use client"

import { useLayoutEffect } from "react"
import { arrive, clearArrival, pendingArrival } from "./arrival"

export function SectionArrival() {
  useLayoutEffect(() => {
    const section = pendingArrival()
    if (!section) return

    window.scrollTo({ top: 0, behavior: "instant" })
    const target = document.getElementById(section)
    if (!target) {
      clearArrival()
      return
    }

    const arrival = arrive(target)
    arrival.landed.then((landed) => {
      clearArrival()
      if (landed) target.focus({ preventScroll: true })
    })
    return arrival.cancel
  }, [])

  return null
}
