"use client"

import { useEffect, useRef } from "react"

const TOKENS = [
  "--color-pink",
  "--color-purple",
  "--color-blue",
  "--color-mint",
  "--color-yellow",
  "--color-orange",
]
const FALLBACK = ["#f7a8c9", "#b39dff", "#9ccaff", "#9fe8cf", "#ffe38a", "#ffb98a"]

type Piece = {
  x: number
  y: number
  vx: number
  vy: number
  w: number
  h: number
  rot: number
  vr: number
  color: string
  circle: boolean
  flip: number
  vflip: number
}

type Props = {
  count?: number
  duration?: number
}

export function Confetti({ count = 90, duration = 4000 }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current
    if (!canvas) return
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      canvas.remove()
      return
    }
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const style = getComputedStyle(document.documentElement)
    const colors = TOKENS.map((t, i) => style.getPropertyValue(t).trim() || FALLBACK[i])

    let width = 0
    let height = 0
    const resize = () => {
      width = window.innerWidth
      height = window.innerHeight
      canvas.width = width * dpr
      canvas.height = height * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    }
    resize()
    window.addEventListener("resize", resize)

    const rand = (a: number, b: number) => a + Math.random() * (b - a)
    const burst = (x: number, y: number, angle: number): Piece[] =>
      Array.from({ length: count }, () => {
        const a = angle + rand(-0.45, 0.45)
        const speed = rand(9, 17)
        return {
          x,
          y,
          vx: Math.cos(a) * speed,
          vy: Math.sin(a) * speed,
          w: rand(6, 11),
          h: rand(8, 16),
          rot: rand(0, Math.PI * 2),
          vr: rand(-0.2, 0.2),
          color: colors[Math.floor(Math.random() * colors.length)],
          circle: Math.random() < 0.25,
          flip: rand(0, Math.PI * 2),
          vflip: rand(0.15, 0.35),
        }
      })

    const pieces = [
      ...burst(0, height, -Math.PI / 3), // bottom-left, up and to the right
      ...burst(width, height, (-2 * Math.PI) / 3), // bottom-right, up and to the left
    ]

    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const elapsed = now - start
      ctx.clearRect(0, 0, width, height)

      let alive = false
      for (const p of pieces) {
        p.vy += 0.32 // gravity
        p.vx *= 0.985 // air drag
        p.vy *= 0.985
        p.x += p.vx
        p.y += p.vy
        p.rot += p.vr
        p.flip += p.vflip
        if (p.y < height + 20) alive = true

        ctx.save()
        ctx.translate(p.x, p.y)
        ctx.rotate(p.rot)
        ctx.scale(1, Math.abs(Math.cos(p.flip)) * 0.85 + 0.15)
        ctx.fillStyle = p.color
        if (p.circle) {
          ctx.beginPath()
          ctx.arc(0, 0, p.w / 2, 0, Math.PI * 2)
          ctx.fill()
        } else {
          ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h)
        }
        ctx.restore()
      }

      if (alive && elapsed < duration) {
        raf = requestAnimationFrame(tick)
      } else {
        canvas.remove()
      }
    }
    raf = requestAnimationFrame(tick)

    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener("resize", resize)
    }
  }, [count, duration])

  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-50">
      <canvas ref={ref} className="h-full w-full" />
    </div>
  )
}
