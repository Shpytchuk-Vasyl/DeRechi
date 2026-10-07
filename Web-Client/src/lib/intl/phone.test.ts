import { describe, expect, it } from "vitest"
import { PHONE_PATTERN } from "@/schema/report-schema"
import { toInternational } from "./phone"

describe("toInternational", () => {
  it.each([
    ["UA", "067 123 45 67", "+380671234567"],
    ["UA", "0671234567", "+380671234567"],
    ["UA", "380671234567", "+380671234567"],
    ["UA", "671234567", "+380671234567"],
    ["PL", "512 345 678", "+48512345678"],
    ["PL", "48512345678", "+48512345678"],
    ["DE", "0151 12345678", "+4915112345678"],
    ["DE", "4915112345678", "+4915112345678"],
    ["FR", "06 12 34 56 78", "+33612345678"],
  ])("completes a number typed in %s without a plus: %s", (country, raw, expected) => {
    expect(toInternational(raw, country)).toBe(expected)
    expect(PHONE_PATTERN.test(expected)).toBe(true)
  })

  it("keeps a number that already has a plus, minus the separators", () => {
    expect(toInternational("+48 512-345-678", "UA")).toBe("+48512345678")
  })

  it("reads 00 as the international prefix, whatever the country", () => {
    expect(toInternational("0048512345678", "UA")).toBe("+48512345678")
  })

  it("leaves the number as typed when the country's calling code is unknown", () => {
    expect(toInternational("0612345678", "XX")).toBe("0612345678")
  })

  it("leaves an empty field empty", () => {
    expect(toInternational("", "UA")).toBe("")
    expect(toInternational("  ", "UA")).toBe("")
  })
})
