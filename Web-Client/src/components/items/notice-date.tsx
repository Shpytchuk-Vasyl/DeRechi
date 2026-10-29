"use client"

import { useFormatter, useLocale } from "next-intl"
import { useSyncExternalStore } from "react"
import { formatNoticeDate, fromIsoDate, todayIso } from "@/lib/intl/dates"

const RELATIVE_DAYS = 7

function subscribe() {
  return () => {}
}

export function NoticeDate({ isoDay }: { isoDay: string }) {
  const format = useFormatter()
  const locale = useLocale()
  const today = fromIsoDate(useSyncExternalStore(subscribe, todayIso, () => null))
  const day = fromIsoDate(isoDay)
  if (!day) return null

  const days = today ? Math.round((today.getTime() - day.getTime()) / 86_400_000) : undefined
  const when =
    days !== undefined && days <= RELATIVE_DAYS
      ? new Intl.RelativeTimeFormat(locale, { numeric: "auto" }).format(-days, "day")
      : formatNoticeDate(format, day, today)

  return <time dateTime={isoDay}>{when}</time>
}
