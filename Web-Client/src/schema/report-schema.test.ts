import { describe, expect, it } from "vitest"
import { DATE_WITHIN_DAYS, draftSchema, reportSchema, todayIso, toItemInput } from "./report-schema"

const valid = {
  title: "Brown leather wallet",
  description: "Worn corners, brass clasp.",
  date: todayIso(-1),
  compensation: 500,
  image: "items/2026/09/abc.jpg",
  categoryId: "4",
  place: {
    id: "ChIJ123",
    name: "Kyiv, Khreshchatyk 22",
    lat: 50.45,
    lon: 30.52,
    countryCode: "UA",
  },
  contact: { phone: "+380671234567", email: "olena@example.com", socialMedias: ["TELEGRAM"] },
}

function errorFor(kind: "lost" | "found", values: Record<string, unknown>): string | undefined {
  const result = reportSchema(kind).safeParse(values)
  return result.success ? undefined : result.error.issues[0]?.message
}

describe("reportSchema", () => {
  it("accepts a complete notice", () => {
    expect(reportSchema("lost").safeParse(valid).success).toBe(true)
  })

  it("lets a lost notice go without a photo", () => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()
    expect(reportSchema("lost").safeParse(withoutImage).success).toBe(true)
  })

  it("insists on a photo for a found notice, the way Client-API does", () => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()
    expect(errorFor("found", withoutImage)).toBe("photoRequired")
  })

  it("rejects a date in the future", () => {
    expect(errorFor("lost", { ...valid, date: todayIso(1) })).toBe("future")
  })

  it("rejects a date older than the window Client-API accepts", () => {
    expect(errorFor("lost", { ...valid, date: todayIso(-DATE_WITHIN_DAYS - 1) })).toBe("tooOld")
    expect(
      reportSchema("lost").safeParse({ ...valid, date: todayIso(-DATE_WITHIN_DAYS) }).success,
    ).toBe(true)
  })

  it("wants a phone in E.164", () => {
    expect(errorFor("lost", { ...valid, contact: { ...valid.contact, phone: "0671234567" } })).toBe(
      "phoneFormat",
    )
  })

  it("rejects a place without a country", () => {
    const { countryCode, ...placeWithoutCountry } = valid.place
    expect(countryCode).toBe("UA")
    expect(errorFor("lost", { ...valid, place: placeWithoutCountry })).toBe("placeRequired")
    expect(errorFor("lost", { ...valid, place: { ...valid.place, countryCode: "POL" } })).toBe(
      "placeRequired",
    )
  })

  it("takes an optional ISO 4217 currency for the reward", () => {
    expect(reportSchema("lost").safeParse({ ...valid, currency: "PLN" }).success).toBe(true)
    expect(errorFor("lost", { ...valid, currency: "zł" })).toBe("invalid")
  })

  it("rejects a place without coordinates", () => {
    const { lat, ...placeWithoutLat } = valid.place
    expect(lat).toBeTypeOf("number")
    expect(errorFor("lost", { ...valid, place: placeWithoutLat })).toBe("placeRequired")
  })

  it("keeps the title within the column width", () => {
    expect(errorFor("lost", { ...valid, title: "x".repeat(101) })).toBe("tooLong")
  })
})

describe("toItemInput", () => {
  it("sends empty optional fields as null, not as empty strings", () => {
    const parsed = reportSchema("lost").parse({
      ...valid,
      description: "",
      image: "",
      compensation: undefined,
      contact: { phone: valid.contact.phone, email: valid.contact.email },
    })

    expect(toItemInput(parsed)).toMatchObject({
      description: null,
      image: null,
      compensation: null,
      contact: { socialMedias: null },
    })
  })

  it("passes the place through as PlaceInput expects it", () => {
    const parsed = reportSchema("lost").parse(valid)
    expect(toItemInput(parsed).place).toEqual({
      id: "ChIJ123",
      name: "Kyiv, Khreshchatyk 22",
      lat: 50.45,
      lon: 30.52,
      countryCode: "UA",
    })
  })

  it("sends the reward as MoneyInput and leaves the currency to the place's country", () => {
    expect(toItemInput(reportSchema("lost").parse(valid)).compensation).toEqual({
      amount: 500,
      currency: null,
    })
    expect(
      toItemInput(reportSchema("lost").parse({ ...valid, currency: "PLN" })).compensation,
    ).toEqual({ amount: 500, currency: "PLN" })
  })

  it("keeps the chosen messengers", () => {
    const parsed = reportSchema("lost").parse(valid)
    expect(toItemInput(parsed).contact.socialMedias).toEqual(["TELEGRAM"])
  })
})

describe("draftSchema", () => {
  it("accepts a found notice with no image yet, because the file is still local", () => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()
    expect(draftSchema("found").safeParse({ ...withoutImage, consent: true }).success).toBe(true)
  })

  it.each([false, undefined])("requires the consent box to be ticked, not %s", (consent) => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()

    const result = draftSchema("found").safeParse({ ...withoutImage, consent })

    expect(result.success).toBe(false)
    expect(result.success ? undefined : result.error.issues[0]?.message).toBe("consent")
  })

  it("still enforces everything else", () => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()

    const result = draftSchema("found").safeParse({
      ...withoutImage,
      consent: true,
      contact: { ...valid.contact, phone: "0671234567" },
    })

    expect(result.success).toBe(false)
    expect(result.success ? undefined : result.error.issues[0]?.message).toBe("phoneFormat")
  })

  it("keeps consent out of what the server action sends to Client-API", () => {
    const parsed = reportSchema("found").parse({ ...valid, consent: true })
    expect(parsed).not.toHaveProperty("consent")
  })

  it("leaves the image rule to the server schema", () => {
    const { image, ...withoutImage } = valid
    expect(image).toBeTruthy()
    expect(reportSchema("found").safeParse(withoutImage).success).toBe(false)
  })
})
