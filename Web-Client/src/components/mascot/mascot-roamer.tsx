"use client"

import { cx } from "class-variance-authority"
import { useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import { useEffect, useRef, useState } from "react"
import { type Look, Znaida } from "@/components/mascot/znaida"
import { toast } from "@/components/pouf/toaster"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export function MascotRoamer() {
  const t = useTranslations("game")
  const router = useRouter()
  const reduceMotion = useReducedMotion()
  const { visit, hide } = useRoaming(!reduceMotion)

  function catchHim() {
    hide()
    const { count, prize } = recordCatch()
    if (prize) {
      router.push(paths.prize)
      return
    }
    toast.success(t("caught", { count, total: CATCHES }))
  }

  if (!visit) return null

  const out = visit.phase === "out"

  return (
    <div
      className="pointer-events-none absolute z-30 overflow-hidden"
      style={{ left: visit.x, top: visit.y, width: SIZE, height: SIZE }}
    >
      <button
        type="button"
        aria-label={t("catch")}
        onClick={catchHim}
        disabled={!out}
        className={cx(
          "znaida-roam pointer-events-auto absolute inset-x-0 top-0.5 cursor-pointer border-none bg-transparent p-0",
          `znaida-roam-${visit.side}`,
          out && "znaida-roam-out znaida-alive",
        )}
      >
        <Znaida look={visit.look} className="h-auto w-full" aria-hidden />
      </button>
    </div>
  )
}

type Side = "top" | "left" | "right"

type Visit = {
  side: Side
  x: number
  y: number
  phase: "hidden" | "out" | "in"
  look: Look
}

const SIZE = 120
const SLIDE_MS = 600
const LIVE_MS = 2600
const GLANCES: Look[] = ["left", "right", "center", "left"]
const GLANCE_MS = 550

function useRoaming(enabled: boolean) {
  const [visit, setVisit] = useState<Visit | null>(null)
  const hideRef = useRef<() => void>(() => {})

  useEffect(() => {
    if (!enabled) return

    const timers = createTimers()
    let lastSpot: Element | null = null
    const patch = (changes: Partial<Visit>) =>
      setVisit((current) => (current ? { ...current, ...changes } : current))

    function hide() {
      timers.clear()
      hideRef.current = () => {}
      patch({ phase: "in" })
      timers.after(SLIDE_MS, () => {
        setVisit(null)
        timers.after(rand(4000, 9000), tick)
      })
    }

    function appear(perch: Perch) {
      lastSpot = perch.spot
      setVisit({ side: perch.side, x: perch.x, y: perch.y, phase: "hidden", look: "center" })
      afterPaint(() => patch({ phase: "out" }))
      GLANCES.forEach((look, index) => {
        timers.after(SLIDE_MS + 300 + index * GLANCE_MS, () => patch({ look }))
      })
      timers.after(SLIDE_MS + LIVE_MS, hide)
      hideRef.current = hide
    }

    function tick() {
      const perch = findPerch(lastSpot)
      if (perch) appear(perch)
      else timers.after(3000, tick)
    }

    timers.after(rand(1500, 3000), tick)
    return timers.clear
  }, [enabled])

  return { visit, hide: () => hideRef.current() }
}

type Perch = { spot: Element; side: Side; x: number; y: number }

const MARGIN = 80

function findPerch(exclude: Element | null): Perch | null {
  const vh = window.innerHeight
  const vw = window.innerWidth
  const options: Perch[] = []

  for (const spot of document.querySelectorAll("[data-mascot-spot]")) {
    if (spot === exclude) continue
    const r = spot.getBoundingClientRect()
    if (r.bottom < 0 || r.top > vh) continue

    if (r.top > MARGIN + SIZE && r.top < vh - MARGIN) {
      const x = rand(r.left + r.width * 0.15, r.right - r.width * 0.15)
      options.push({ spot, side: "top", x: x - SIZE / 2, y: r.top - SIZE })
    }

    const y = rand(r.top + r.height * 0.2, r.bottom - r.height * 0.2)
    if (y > MARGIN && y < vh - MARGIN) {
      if (r.left > SIZE + 8) options.push({ spot, side: "left", x: r.left - SIZE, y: y - SIZE / 2 })
      if (vw - r.right > SIZE + 8)
        options.push({ spot, side: "right", x: r.right, y: y - SIZE / 2 })
    }
  }

  const chosen = pick(options)
  if (!chosen) return null
  return { ...chosen, x: chosen.x + window.scrollX, y: chosen.y + window.scrollY }
}

const CATCHES = 5
const CAUGHT_KEY = "znaida-caught"

function recordCatch(): { count: number; prize: boolean } {
  let count = 0
  try {
    count = (Number(localStorage.getItem(CAUGHT_KEY)) || 0) + 1
    localStorage.setItem(CAUGHT_KEY, String(count >= CATCHES ? 0 : count))
  } catch {}
  return { count, prize: count >= CATCHES }
}

const rand = (min: number, max: number) => min + Math.random() * (max - min)

const pick = <T,>(items: T[]): T | undefined => items[Math.floor(Math.random() * items.length)]

function afterPaint(fn: () => void) {
  requestAnimationFrame(() => requestAnimationFrame(fn))
}

function createTimers() {
  const pending = new Set<ReturnType<typeof setTimeout>>()
  return {
    after(ms: number, fn: () => void) {
      const timer = setTimeout(() => {
        pending.delete(timer)
        fn()
      }, ms)
      pending.add(timer)
    },
    clear() {
      for (const timer of pending) clearTimeout(timer)
      pending.clear()
    },
  }
}
