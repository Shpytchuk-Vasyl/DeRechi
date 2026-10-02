import { describe, expect, it } from "vitest"
import { CLAIM_TOKEN, claimSchema, toContactInput } from "./claim-schema"

const valid = { phone: "+380671234567", email: "olena@example.com", socialMedias: ["TELEGRAM"] }

function errorFor(values: Record<string, unknown>): string | undefined {
  const result = claimSchema.safeParse(values)
  return result.success ? undefined : result.error.issues[0]?.message
}

describe("claimSchema", () => {
  it("accepts the same contacts Client-API's ContactInfoInput accepts", () => {
    expect(claimSchema.safeParse(valid).success).toBe(true)
  })

  it("trims the phone before checking it against E.164", () => {
    expect(claimSchema.parse({ ...valid, phone: " +380671234567 " }).phone).toBe("+380671234567")
  })

  it.each(["0671234567", "+0671234567", "+38067", "+38067123456789012"])(
    "rejects %s as a phone",
    (phone) => {
      expect(errorFor({ ...valid, phone })).toBe("phoneFormat")
    },
  )

  it("requires an email, because the author is told by email", () => {
    expect(errorFor({ phone: valid.phone })).toBe("emailFormat")
    expect(errorFor({ ...valid, email: "olena" })).toBe("emailFormat")
  })

  it("keeps the email within the column width", () => {
    expect(errorFor({ ...valid, email: `${"x".repeat(40)}@example.com` })).toBe("tooLong")
  })

  it("accepts only the messengers the API knows", () => {
    expect(claimSchema.safeParse({ ...valid, socialMedias: ["SIGNAL"] }).success).toBe(false)
    expect(claimSchema.safeParse({ phone: valid.phone, email: valid.email }).success).toBe(true)
  })
})

describe("CLAIM_TOKEN", () => {
  it("accepts the UUID Client-API issues", () => {
    expect(CLAIM_TOKEN.test("6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11")).toBe(true)
  })

  it.each([
    ["an upper-case UUID", "6F1C2A52-0D7E-4C1E-9A43-6F0D4F3A9B11"],
    ["an old payment code", "DR-7K3M9Q"],
    ["the old cookie value", "1"],
    ["a UUID with trailing text", "6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11; Path=/"],
    ["a UUID without dashes", "6f1c2a520d7e4c1e9a436f0d4f3a9b11"],
  ])("rejects %s", (_, value) => {
    expect(CLAIM_TOKEN.test(value)).toBe(false)
  })
})

describe("toContactInput", () => {
  it("sends no messengers as null rather than an empty list", () => {
    expect(
      toContactInput({ ...claimSchema.parse(valid), socialMedias: [] }).socialMedias,
    ).toBeNull()
    expect(
      toContactInput(claimSchema.parse({ phone: valid.phone, email: valid.email })).socialMedias,
    ).toBeNull()
  })

  it("keeps the chosen messengers", () => {
    expect(toContactInput(claimSchema.parse(valid))).toEqual({
      phone: "+380671234567",
      email: "olena@example.com",
      socialMedias: ["TELEGRAM"],
    })
  })
})
