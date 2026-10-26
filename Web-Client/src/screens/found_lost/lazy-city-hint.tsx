"use client"

import dynamic from "next/dynamic"
import { useEffect, useState } from "react"
import type { ItemKind } from "@/api/items"
import { isCityHintPaused } from "@/lib/browser-storage"

const CityHint = dynamic(() => import("@/screens/found_lost/city-hint"), { ssr: false })

const SHOW_AFTER_PX = 240

export function LazyCityHint({ kind, city }: { kind: ItemKind; city?: string }) {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    if (isCityHintPaused()) return

    function reveal() {
      if (window.scrollY < SHOW_AFTER_PX) return
      window.removeEventListener("scroll", reveal)
      setScrolled(true)
    }

    window.addEventListener("scroll", reveal, { passive: true })
    return () => window.removeEventListener("scroll", reveal)
  }, [])

  return scrolled ? <CityHint kind={kind} city={city} /> : null
}
