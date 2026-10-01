"use client"

import { useReducedMotion } from "framer-motion"
import { useFormatter } from "next-intl"
import { useEffect, useRef } from "react"

const DURATION_MS = 1200

const easeOutCubic = (t: number) => 1 - (1 - t) ** 3

export function CountUp({ value, className }: { value: number; className?: string }) {
  const format = useFormatter()
  const reduceMotion = useReducedMotion()
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const el = ref.current
    if (!el || reduceMotion) return

    const show = (n: number) => {
      el.textContent = format.number(n)
    }

    let frame = 0
    const run = () => {
      const start = performance.now()
      const step = (now: number) => {
        const t = Math.min(1, (now - start) / DURATION_MS)
        show(Math.round(value * easeOutCubic(t)))
        if (t < 1) frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    }

    show(0)
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          observer.disconnect()
          run()
        }
      },
      { threshold: 0.6 },
    )
    observer.observe(el)

    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
      show(value)
    }
  }, [value, reduceMotion, format])

  return (
    <span ref={ref} className={className}>
      {format.number(value)}
    </span>
  )
}
