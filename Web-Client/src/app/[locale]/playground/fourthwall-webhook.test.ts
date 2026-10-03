import { describe, expect, it } from "vitest"
import { fakeOrderPlaced, signFourthwallBody, variantIdOf } from "./fourthwall-webhook"

describe("fourthwall webhook helpers", () => {
  it("signs the raw body with HMAC-SHA256 in base64, the same way Client-API checks it", () => {
    expect(signFourthwallBody("webhook-secret-value", '{"type":"ORDER_PLACED"}')).toBe(
      "vn7Twts8G/01T3mcxrh7KYY8SxXEM5zCczBRuYSRdaQ=",
    )
  })

  it("reads the variant id out of a checkout URL or accepts a bare id", () => {
    expect(
      variantIdOf(
        "https://derechi-shop.fourthwall.com/cart/checkout?products=var-1:1&currency=USD",
      ),
    ).toBe("var-1")
    expect(variantIdOf("  var-2 ")).toBe("var-2")
    expect(variantIdOf("")).toBeNull()
    expect(variantIdOf("not a variant id")).toBeNull()
  })

  it("builds an ORDER_PLACED event that names the variant the claim was given", () => {
    const now = new Date("2026-10-03T10:00:00Z")

    const event = fakeOrderPlaced({ variantId: "var-1", now, orderId: "pg-123456" })

    expect(event.type).toBe("ORDER_PLACED")
    expect(event.testMode).toBe(true)
    expect(event.data.id).toBe("pg-123456")
    expect(event.data.friendlyId).toBe("PG-123456")
    expect(event.data.status).toBe("COMPLETED")
    expect(event.data.createdAt).toBe("2026-10-03T10:00:00.000Z")
    expect(event.data.amounts.total).toEqual({ value: 1, currency: "USD" })
    expect(event.data.offers[0]?.variant.id).toBe("var-1")
  })
})
