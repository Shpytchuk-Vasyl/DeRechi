import { describe, expect, it } from "vitest"
import { countWords, readingMinutes, WORDS_PER_MINUTE } from "./reading-time"

describe("reading time", () => {
  it("counts words across texts, ignoring extra whitespace", () => {
    expect(countWords(["Загубили  ключі", " що робити\n"])).toBe(4)
    expect(countWords([])).toBe(0)
  })

  it("rounds to whole minutes and never says zero", () => {
    expect(readingMinutes([])).toBe(1)
    expect(readingMinutes(["слово"])).toBe(1)
    expect(
      readingMinutes([
        Array(WORDS_PER_MINUTE * 3)
          .fill("слово")
          .join(" "),
      ]),
    ).toBe(3)
    expect(
      readingMinutes([
        Array(Math.round(WORDS_PER_MINUTE * 2.4))
          .fill("a")
          .join(" "),
      ]),
    ).toBe(2)
  })
})
