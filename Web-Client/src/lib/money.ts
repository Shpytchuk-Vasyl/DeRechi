type NumberFormatter = {
  number(value: number, options?: Intl.NumberFormatOptions): string
}

export function formatHryvnia(format: NumberFormatter, amount: number): string {
  return format.number(amount, { style: "currency", currency: "UAH", maximumFractionDigits: 0 })
}
