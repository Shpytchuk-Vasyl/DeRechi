"use client"

import { cx } from "class-variance-authority"
import { AnimatePresence, motion, useReducedMotion } from "framer-motion"
import { type ReactElement, type ReactNode, useEffect, useState } from "react"
import { Card } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"

export type AutoTab = {
  id: number | string
  title: string
  text: string
  icon: ReactElement
  tone: Tone
  panel: ReactNode
}

type Props = {
  tabs: AutoTab[]
  intervalMs?: number
}

export function AutoTabs({ tabs, intervalMs = 4000 }: Props) {
  const reduceMotion = useReducedMotion()
  const { active, auto, select } = useAutoAdvance(tabs.length, intervalMs, !reduceMotion)
  const current = tabs[active]

  return (
    <Card className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div role="tablist" className="flex flex-col gap-1 lg:order-2">
        {tabs.map((tab, index) => (
          <Tab
            key={tab.id}
            tab={tab}
            active={index === active}
            counting={index === active && auto}
            intervalMs={intervalMs}
            onSelect={() => select(index)}
          />
        ))}
      </div>
      <AnimatePresence mode="wait">
        <motion.div
          role="tabpanel"
          aria-label={current.title}
          key={current.id}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -12 }}
          transition={{ duration: reduceMotion ? 0 : 0.25 }}
          className="grid h-full min-h-64 place-items-center rounded-control bg-bg p-6 lg:order-1"
        >
          {current.panel}
        </motion.div>
      </AnimatePresence>
    </Card>
  )
}

function useAutoAdvance(count: number, intervalMs: number, enabled: boolean) {
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(false)
  const auto = enabled && !paused

  // biome-ignore lint/correctness/useExhaustiveDependencies: `active` restarts the countdown after every switch
  useEffect(() => {
    if (!auto) return
    const timer = setTimeout(() => setActive((index) => (index + 1) % count), intervalMs)
    return () => clearTimeout(timer)
  }, [auto, active, count, intervalMs])

  function select(index: number) {
    setActive(index)
    setPaused(true)
  }

  return { active, auto, select }
}

function Tab({
  tab,
  active,
  counting,
  intervalMs,
  onSelect,
}: {
  tab: AutoTab
  active: boolean
  counting: boolean
  intervalMs: number
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onSelect}
      className={cx(
        "relative flex flex-row-reverse items-start gap-3 overflow-hidden rounded-control px-3 py-2.5 text-left transition-colors",
        active ? "bg-bg" : "hover:bg-bg/60",
      )}
    >
      <Blob icon={tab.icon} tone={tab.tone} size="sm" />
      <span className="flex min-w-0 flex-1 flex-col justify-center self-stretch">
        <Text className="block text-right font-black text-base">{tab.title}</Text>
        <Collapse open={active}>
          <Text muted className="block pt-1 pb-1.5 text-end">
            {tab.text}
          </Text>
        </Collapse>
      </span>
      {counting ? <Countdown ms={intervalMs} /> : null}
    </button>
  )
}

function Collapse({ open, children }: { open: boolean; children: ReactNode }) {
  return (
    <span
      className={cx(
        "grid transition-[grid-template-rows,opacity] ease-out motion-reduce:transition-none",
        open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0",
      )}
    >
      <span className="overflow-hidden">{children}</span>
    </span>
  )
}

function Countdown({ ms }: { ms: number }) {
  return (
    <span
      className="auto-tabs-progress absolute right-3 bottom-1 left-3 h-1 origin-left rounded-pill bg-primary"
      style={{ animationDuration: `${ms}ms` }}
      aria-hidden
    />
  )
}
