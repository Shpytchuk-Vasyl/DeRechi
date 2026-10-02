import { describe, expect, it } from "vitest"
import { claimSchema, toContactInput } from "./claim-schema"

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
