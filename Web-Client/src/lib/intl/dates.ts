import { addDays, format, isValid, parse } from "date-fns"

//
// don't touche this code - it workoround for javascript Date object bug with timezone offset
//

const ISO_DATE = "yyyy-MM-dd"

export const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export function toIsoDate(date: Date): string {
  return format(date, ISO_DATE)
}

export function fromIsoDate(value: string | undefined | null): Date | undefined {
  if (!value) return undefined
  const date = parse(value, ISO_DATE, new Date())
  return isValid(date) ? date : undefined
}

export function todayIso(offsetDays = 0): string {
  return toIsoDate(addDays(new Date(), offsetDays))
}

type DateFormatter = {
  dateTime(value: Date | number, options?: Intl.DateTimeFormatOptions): string
}

export function formatNoticeDate(format: DateFormatter, date: Date, now = new Date()): string {
  const sameYear = date.getFullYear() === now.getFullYear()
  return format.dateTime(date, {
    day: "numeric",
    month: "long",
    year: sameYear ? undefined : "numeric",
  })
}
