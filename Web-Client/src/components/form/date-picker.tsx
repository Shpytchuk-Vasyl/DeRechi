"use client"

import { cn } from "cn"
import { de, enUS, fr, pl, uk } from "date-fns/locale"
import { CalendarIcon, X } from "lucide-react"
import { useFormatter, useLocale, useTranslations } from "next-intl"
import { Popover as RPopover } from "radix-ui"
import { useState } from "react"
import type { DateRange, Matcher } from "react-day-picker"
import { IconButton } from "@/components/pouf/Button"
import { inputClasses } from "@/components/pouf/Input"
import { Calendar } from "@/components/ui/calendar"
import type { Locale as AppLocale } from "@/i18n/routing"
import { fromIsoDate, toIsoDate } from "@/lib/intl/dates"

const CALENDAR_LOCALES = { en: enUS, uk, pl, de, fr }

const trigger = (invalid?: boolean) =>
  cn(
    inputClasses({ invalid: Boolean(invalid) }),
    "inline-flex min-w-0 items-center gap-2 text-left",
  )

type RangeProps = {
  from?: string
  to?: string
  onChange: (range: { from?: string; to?: string }) => void
  placeholder: string
  ariaLabel: string
  id?: string
  inline?: boolean
  max?: string
}

export function DateRangePicker({
  from,
  to,
  onChange,
  placeholder,
  ariaLabel,
  id,
  inline = false,
  max,
}: RangeProps) {
  const locale = useLocale() as AppLocale
  const format = useFormatter()
  const t = useTranslations("list")
  const [open, setOpen] = useState(false)

  const selected: DateRange | undefined =
    from || to ? { from: fromIsoDate(from), to: fromIsoDate(to) } : undefined
  const after = fromIsoDate(max)

  function label(): string {
    const start = fromIsoDate(from)
    const end = fromIsoDate(to)
    if (!start && !end) return placeholder

    const show = (date?: Date) => (date ? format.dateTime(date, { dateStyle: "medium" }) : "…")
    return `${show(start)} – ${show(end)}`
  }

  const calendar = (months: number, className: string) => (
    <Calendar
      mode="range"
      numberOfMonths={months}
      defaultMonth={fromIsoDate(from)}
      selected={selected}
      onSelect={(range) =>
        onChange({
          from: range?.from ? toIsoDate(range.from) : undefined,
          to: range?.to ? toIsoDate(range.to) : undefined,
        })
      }
      locale={CALENDAR_LOCALES[locale]}
      disabled={after ? { after } : undefined}
      className={className}
    />
  )

  const triggerButton = (
    <button
      id={id}
      type="button"
      data-slot="date-trigger"
      aria-label={ariaLabel}
      aria-expanded={inline ? open : undefined}
      onClick={inline ? () => setOpen((value) => !value) : undefined}
      className={cn(trigger(), from || to ? "pr-12" : "text-muted")}
    >
      <CalendarIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <span className="truncate">{label()}</span>
    </button>
  )

  const clearButton =
    from || to ? (
      <span className="absolute top-1/2 right-2 -translate-y-1/2">
        <IconButton
          size="sm"
          label={t("reset")}
          icon={<X className="size-3.5" />}
          onClick={() => onChange({ from: undefined, to: undefined })}
        />
      </span>
    ) : null

  if (inline) {
    return (
      <div className="flex flex-col gap-2">
        <div className="relative">
          {triggerButton}
          {clearButton}
        </div>
        {open ? (
          <div className="rounded-control bg-bg p-2">
            {calendar(
              1,
              "w-full! bg-transparent p-1 [--cell-size:clamp(2.25rem,calc((100vw-6rem)/7),2.75rem)]",
            )}
          </div>
        ) : null}
      </div>
    )
  }

  return (
    <RPopover.Root open={open} onOpenChange={setOpen}>
      <div className="relative">
        <RPopover.Trigger asChild>{triggerButton}</RPopover.Trigger>
        {clearButton}
      </div>

      <RPopover.Portal>
        <RPopover.Content className="pouf-popover" sideOffset={8} align="start">
          {calendar(2, "p-1")}
        </RPopover.Content>
      </RPopover.Portal>
    </RPopover.Root>
  )
}

type SingleProps = {
  value?: string
  onChange: (value: string | undefined) => void
  placeholder: string
  min?: string
  max?: string
  invalid?: boolean
  id?: string
}

export function DatePicker({ value, onChange, placeholder, min, max, invalid, id }: SingleProps) {
  const locale = useLocale() as AppLocale
  const format = useFormatter()
  const [open, setOpen] = useState(false)

  const selected = fromIsoDate(value)

  const disabled: Matcher[] = []
  const before = fromIsoDate(min)
  if (before) disabled.push({ before })
  const after = fromIsoDate(max)
  if (after) disabled.push({ after })

  return (
    <RPopover.Root open={open} onOpenChange={setOpen}>
      <RPopover.Trigger asChild>
        <button
          id={id}
          type="button"
          data-slot="date-trigger"
          aria-invalid={invalid}
          className={cn(trigger(invalid), !selected && "text-muted")}
        >
          <CalendarIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span className="truncate">
            {selected ? format.dateTime(selected, { dateStyle: "long" }) : placeholder}
          </span>
        </button>
      </RPopover.Trigger>

      <RPopover.Portal>
        <RPopover.Content className="pouf-popover" sideOffset={8} align="start">
          <Calendar
            mode="single"
            defaultMonth={selected}
            selected={selected}
            onSelect={(date) => {
              onChange(date ? toIsoDate(date) : undefined)
              setOpen(false)
            }}
            disabled={disabled}
            locale={CALENDAR_LOCALES[locale]}
            className="p-1"
          />
        </RPopover.Content>
      </RPopover.Portal>
    </RPopover.Root>
  )
}

type FormFieldProps = Omit<SingleProps, "value" | "onChange"> & {
  name: string
  defaultValue?: string
}

export function DateFormField({ name, defaultValue, ...props }: FormFieldProps) {
  const [value, setValue] = useState(defaultValue)

  return (
    <>
      <input type="hidden" name={name} value={value ?? ""} readOnly />
      <DatePicker {...props} value={value} onChange={setValue} />
    </>
  )
}
