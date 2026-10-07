export const CALLING_CODES: Readonly<Record<string, string>> = {
  UA: "380",
  PL: "48",
  DE: "49",
  FR: "33",
}

const MIN_NATIONAL_DIGITS = 9

export function toInternational(raw: string, countryCode: string): string {
  const value = raw.trim()
  if (value.startsWith("+")) return `+${value.slice(1).replace(/\D/g, "")}`

  const digits = value.replace(/\D/g, "")
  if (digits === "") return value
  if (digits.startsWith("00")) return `+${digits.slice(2)}`

  const code = CALLING_CODES[countryCode]
  if (!code) return value
  if (digits.startsWith("0")) return `+${code}${digits.slice(1)}`
  if (digits.startsWith(code) && digits.length >= code.length + MIN_NATIONAL_DIGITS)
    return `+${digits}`
  return `+${code}${digits}`
}
