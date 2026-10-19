"use client"

import { useEffect, useRef } from "react"
import type { UseFormReset, UseFormWatch } from "react-hook-form"
import type { ItemKind } from "@/api/items"
import { clearReportDraft, loadReportDraft, saveReportDraft } from "@/lib/browser-storage"
import type { ReportDraft } from "@/schema/report-schema"

const SAVE_DELAY_MS = 400

type Options = {
  kind: ItemKind
  step: number
  watch: UseFormWatch<ReportDraft>
  reset: UseFormReset<ReportDraft>
  onRestore: (step: number) => void
}

export function useReportDraft({ kind, step, watch, reset, onRestore }: Options) {
  const stepRef = useRef(step)
  stepRef.current = step
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // biome-ignore lint/correctness/useExhaustiveDependencies: mount only
  useEffect(() => {
    const handle = setTimeout(() => {
      const saved = loadReportDraft(kind)
      if (!saved) return
      try {
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
        saveReportDraft(kind, stepRef.current, values)
      }, SAVE_DELAY_MS)
    })
    return () => {
      subscription.unsubscribe()
    }
  }, [watch, kind])

  function clear() {
    if (timer.current !== null) clearTimeout(timer.current)
    timer.current = null
    clearReportDraft(kind)
  }

  return { clear }
}
