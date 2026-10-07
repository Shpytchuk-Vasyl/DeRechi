"use client"

import { useTranslations } from "next-intl"
import { useCallback, useRef } from "react"
import { toast } from "@/components/pouf/toaster"
import { clientEnv } from "@/lib/env/client"

const DURATION_MS = 10_000

export function useSmsOutageNotice() {
  const t = useTranslations("smsOutage")
  const shown = useRef(false)

  return useCallback(() => {
    if (!clientEnv.NEXT_PUBLIC_SMS_OUTAGE || shown.current) return
    shown.current = true
    toast.warning(t("title"), { description: t("text"), duration: DURATION_MS })
  }, [t])
}
