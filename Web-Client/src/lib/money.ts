import type { Money } from "@/lib/country"

type NumberFormatter = {
  number(value: number, options?: Intl.NumberFormatOptions): string
}

export function formatMoney(format: NumberFormatter, money: Money): string {
  return format.number(money.amount, {
    style: "currency",
    currency: money.currency,
    maximumFractionDigits: 0,
  })
}
