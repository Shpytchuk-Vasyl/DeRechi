"use client"

import { cx } from "class-variance-authority"
import { useEffect, useState } from "react"
import { type Look, Znaida } from "@/components/mascot/znaida"
import { Button } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { Heading, Text } from "@/components/pouf/text"

const LOOKS: Look[] = ["left", "center", "right"]
const SIDES = ["top", "left", "right"] as const
type Side = (typeof SIDES)[number]

export function ZnaidaLab() {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <FaceBench />
      <PeekBench />
    </div>
  )
}

function FaceBench() {
  const [look, setLook] = useState<Look>("center")
  const [auto, setAuto] = useState(true)
  const [alive, setAlive] = useState(true)
  const [size, setSize] = useState(320)

  useEffect(() => {
    if (!auto) return
    const timer = setInterval(() => setLook(LOOKS[Math.floor(Math.random() * LOOKS.length)]), 900)
    return () => clearInterval(timer)
  }, [auto])

  return (
    <Card>
      <div className="flex flex-col gap-5">
        <Heading level={2}>Обличчя</Heading>
        <div className="grid place-items-center rounded-control bg-bg py-8">
          <div className={cx(alive && "znaida-alive")} style={{ width: size }}>
            <Znaida look={look} className="h-auto w-full" />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Text size="sm" muted>
            Погляд:
          </Text>
          {LOOKS.map((it) => (
            <Button
              key={it}
              size="sm"
              variant={look === it && !auto ? "solid" : "quiet"}
              onClick={() => {
                setAuto(false)
                setLook(it)
              }}
            >
              {it}
            </Button>
          ))}
          <Button size="sm" variant={auto ? "solid" : "quiet"} onClick={() => setAuto((v) => !v)}>
            авто
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant={alive ? "solid" : "quiet"} onClick={() => setAlive((v) => !v)}>
            язичок
          </Button>
          <label className="ml-2 flex items-center gap-2 font-bold text-muted-foreground text-sm">
            Розмір
            <input
              type="range"
              min={48}
              max={480}
              value={size}
              onChange={(event) => setSize(Number(event.target.value))}
            />
            {size}px
          </label>
        </div>
      </div>
    </Card>
  )
}

const SLIDE_MS = 600
const LIVE_MS = 2600
const REST_MS = 900

function PeekBench() {
  const [side, setSide] = useState<Side>("top")
  const [out, setOut] = useState(false)
  const [look, setLook] = useState<Look>("center")
  const [loop, setLoop] = useState(true)

  // biome-ignore lint/correctness/useExhaustiveDependencies: see above
  useEffect(() => {
    if (!loop) return
    let cancelled = false
    const timers: ReturnType<typeof setTimeout>[] = []
    const after = (ms: number, fn: () => void) => timers.push(setTimeout(fn, ms))

    const visit = (index: number) => {
      if (cancelled) return
      setSide(SIDES[index % SIDES.length])
      setLook("center")
      after(50, () => setOut(true))
      after(SLIDE_MS + 300, () => setLook("left"))
      after(SLIDE_MS + 900, () => setLook("right"))
      after(SLIDE_MS + 1500, () => setLook("center"))
      after(SLIDE_MS + LIVE_MS, () => setOut(false))
      after(SLIDE_MS + LIVE_MS + SLIDE_MS + REST_MS, () => visit(index + 1))
    }
    visit(SIDES.indexOf(side))

    return () => {
      cancelled = true
      for (const timer of timers) clearTimeout(timer)
    }
  }, [loop])

  const SIZE = 120
  const box: Record<Side, string> = {
    top: "left-1/2 -translate-x-1/2 -top-[120px]",
    left: "top-1/2 -translate-y-1/2 -left-[120px]",
    right: "top-1/2 -translate-y-1/2 -right-[120px]",
  }

  return (
    <Card>
      <div className="flex flex-col gap-5">
        <Heading level={2}>Визирання</Heading>
        <div className="grid place-items-center rounded-blob bg-bg px-4 py-36">
          <div className="clay-tint clay-found relative h-40 w-64 rounded-card">
            <div
              className={cx("pointer-events-none absolute overflow-hidden", box[side])}
              style={{ width: SIZE, height: SIZE }}
            >
              <div
                className={cx(
                  "znaida-roam absolute",
                  `znaida-roam-${side}`,
                  out && "znaida-roam-out znaida-alive",
                )}
                style={{ width: SIZE, left: 0, top: (SIZE - SIZE * 0.95) / 2 }}
              >
                <Znaida look={look} className="h-auto w-full" />
              </div>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant={loop ? "solid" : "quiet"} onClick={() => setLoop((v) => !v)}>
            цикл
          </Button>
          {SIDES.map((it) => (
            <Button
              key={it}
              size="sm"
              variant={side === it ? "solid" : "quiet"}
              onClick={() => {
                setLoop(false)
                setSide(it)
              }}
            >
              {it}
            </Button>
          ))}
          <Button size="sm" variant="quiet" onClick={() => setOut((v) => !v)} disabled={loop}>
            {out ? "сховати" : "показати"}
          </Button>
        </div>
      </div>
    </Card>
  )
}
