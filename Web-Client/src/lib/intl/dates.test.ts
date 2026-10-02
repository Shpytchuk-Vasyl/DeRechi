import { describe, expect, it } from "vitest"
import { formatNoticeDate, fromIsoDate, toIsoDate } from "./dates"

describe("ISO dates", () => {
  it("reads a date as a local calendar day, not as UTC midnight", () => {
    const date = fromIsoDate("2026-09-01")

    expect(date?.getFullYear()).toBe(2026)
    expect(date?.getMonth()).toBe(8)
    expect(date?.getDate()).toBe(1)
  })

  it("writes the same calendar day back", () => {
    expect(toIsoDate(new Date(2026, 8, 1, 23, 30))).toBe("2026-09-01")
    expect(toIsoDate(fromIsoDate("2026-02-28") as Date)).toBe("2026-02-28")
  })

  it.each([undefined, null, "", "2026-02-30", "01.09.2026"])("gives no date for %s", (value) => {
    expect(fromIsoDate(value)).toBeUndefined()
  })
})

describe("formatNoticeDate", () => {
  const format = {
    dateTime: (_: Date | number, options?: Intl.DateTimeFormatOptions) => JSON.stringify(options),
  }

  it("leaves the year out for this year's notices", () => {
    const options = JSON.parse(formatNoticeDate(format, new Date(2026, 8, 1), new Date(2026, 9, 2)))

    expect(options.year).toBeUndefined()
  })

  it("shows the year for an older notice", () => {
    const options = JSON.parse(
      formatNoticeDate(format, new Date(2025, 11, 31), new Date(2026, 0, 2)),
    )

    expect(options.year).toBe("numeric")
  })
})
