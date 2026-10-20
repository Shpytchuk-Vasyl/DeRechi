"use client"

import { useLayoutEffect } from "react"
import { clearArrival, flyTo, pendingArrival } from "./arrival"

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

    const arrival = flyTo(target)
    arrival.landed.then(clearArrival)
    return arrival.cancel
  }, [])

  return null
}
