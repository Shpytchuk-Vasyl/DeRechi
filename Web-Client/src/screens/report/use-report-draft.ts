"use client"

import { useCallback, useEffect, useRef } from "react"
import type { DeepPartial, UseFormReset, UseFormWatch } from "react-hook-form"
import type { ItemKind } from "@/api/items"
import type { ReportDraft } from "@/schema/report-schema"

const DRAFT_TTL_MS = 5 * 60_000

const SAVE_DELAY_MS = 400

type SavedDraft = { at: number; step: number; values: DeepPartial<ReportDraft> }

type Options = {
  kind: ItemKind
  step: number
  watch: UseFormWatch<ReportDraft>
  reset: UseFormReset<ReportDraft>
  onRestore: (step: number) => void
}

export function useReportDraft({ kind, step, watch, reset, onRestore }: Options) {
  const key = `report-draft:${kind}`
  const stepRef = useRef(step)
  stepRef.current = step
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const write = useCallback(
    (values: DeepPartial<ReportDraft>) => {
      try {
        const draft: SavedDraft = {
          at: Date.now(),
          step: stepRef.current,
          values,
        }
        sessionStorage.setItem(key, JSON.stringify(draft))
      } catch {}
    },
    [key],
  )

  // biome-ignore lint/correctness/useExhaustiveDependencies: mount only
  useEffect(() => {
    const handle = setTimeout(() => {
      try {
        const raw = sessionStorage.getItem(key)
        if (!raw) return
        const saved = JSON.parse(raw) as SavedDraft
        if (Date.now() - saved.at > DRAFT_TTL_MS) {
          sessionStorage.removeItem(key)
          return
        }
        reset(saved.values)
        onRestore(saved.step)
      } catch {}
    }, 0)
    return () => clearTimeout(handle)
  }, [])

  useEffect(() => {
    const subscription = watch((values) => {
      if (timer.current !== null) clearTimeout(timer.current)
      timer.current = setTimeout(() => {
        timer.current = null
        write(values)
      }, SAVE_DELAY_MS)
    })
    return () => {
      subscription.unsubscribe()
    }
  }, [watch, write])

  function clear() {
    if (timer.current !== null) clearTimeout(timer.current)
    timer.current = null
    try {
      sessionStorage.removeItem(key)
    } catch {}
  }

  return { clear }
}
