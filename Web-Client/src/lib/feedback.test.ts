import { describe, expect, it } from "vitest"
import { feedbackForm, feedbackPage, feedbackUrl } from "./feedback"

describe("feedbackForm", () => {
  it("uses a single form for every language", () => {
    expect(feedbackForm("wA1b2C", "uk")).toBe("wA1b2C")
    expect(feedbackForm(" wA1b2C ", "de")).toBe("wA1b2C")
  })

  it("picks the form of the language, then the Ukrainian one, then the first one", () => {
    const spec = "uk=ukForm, en=enForm"
    expect(feedbackForm(spec, "en")).toBe("enForm")
    expect(feedbackForm(spec, "pl")).toBe("ukForm")
    expect(feedbackForm("en=enForm, de=deForm", "fr")).toBe("enForm")
  })

  it("has no form without a value", () => {
    expect(feedbackForm(undefined, "uk")).toBeUndefined()
    expect(feedbackForm(" ", "uk")).toBeUndefined()
  })
})

describe("feedbackPage", () => {
  it("keeps an ordinary path", () => {
    expect(feedbackPage("/lost/42")).toBe("/lost/42")
  })

  it("drops the claim token", () => {
    expect(feedbackPage("/claims/3f1c-secret")).toBe("/claims")
  })
})

describe("feedbackUrl", () => {
  it("opens the form page with the hidden fields", () => {
    expect(feedbackUrl("wA1b2C", { page: "/lost/42" })).toBe(
      "https://tally.so/r/wA1b2C?page=%2Flost%2F42",
    )
  })
})
