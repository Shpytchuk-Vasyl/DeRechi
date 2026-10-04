import { beforeEach, describe, expect, it, vi } from "vitest"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
  FoundItemClaimQuery,
  LostItemClaimQuery,
  UnlockFoundItemClaimMutation,
  UnlockLostItemClaimMutation,
} from "@/graphql/documents"

const mocks = vi.hoisted(() => {
  class GraphQLRequestError extends Error {
    constructor(
      message: string,
      readonly errors: { extensions?: { classification?: string } }[],
    ) {
      super(message)
    }

    get isNotFound(): boolean {
      return this.errors.some((error) => error.extensions?.classification === "NOT_FOUND")
    }

    get isPaymentUnavailable(): boolean {
      return this.errors.some((error) => error.extensions?.classification === "PAYMENT_UNAVAILABLE")
    }
  }

  return {
    GraphQLRequestError,
    graphqlRequest: vi.fn(),
    passesBotCheck: vi.fn(),
    revalidateTag: vi.fn(),
    setCookie: vi.fn(),
  }
})

vi.mock("@/graphql/client", () => ({
  GraphQLRequestError: mocks.GraphQLRequestError,
  graphqlRequest: mocks.graphqlRequest,
}))
vi.mock("@/lib/bot-check", () => ({ passesBotCheck: mocks.passesBotCheck }))
vi.mock("next/cache", () => ({ revalidateTag: mocks.revalidateTag }))
vi.mock("next/headers", () => ({ cookies: async () => ({ set: mocks.setCookie }) }))

const { claimNotice, claimStatus, confirmReturn, unlockClaim } = await import("./claim")

const contact = {
  phone: "+48501234567",
  email: "claimant@example.com",
  socialMedias: ["VIBER", "WHATSAPP"],
}

const CLAIM_ID = "11"
const OTHER_CLAIM_ID = "12"
const OLD_TOKEN = "6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11"
const CHECKOUT = "https://derechi-shop.fourthwall.com/cart/checkout?products=var_1:1"

const unpaid = { checkoutUrl: null, paid: false, contactsSent: false }
const paidUp = { checkoutUrl: CHECKOUT, paid: true, contactsSent: true }

const notFound = () =>
  new mocks.GraphQLRequestError("LostItem.id: 7", [{ extensions: { classification: "NOT_FOUND" } }])

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, "error").mockImplementation(() => {})
  mocks.passesBotCheck.mockResolvedValue(true)
})

describe("claimNotice", () => {
  it("sends the claimant's contacts as ContactInfoInput for a lost notice", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: CLAIM_ID, repeated: false, ...unpaid },
    })

    const result = await claimNotice("lost", "7", contact)

    expect(result).toEqual({ ok: true, repeated: false, claimId: CLAIM_ID, ...unpaid })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimLostItemMutation,
      { id: "7", contact },
      { cache: "no-store" },
    )
  })

  it("uses the found mutation for a found notice and passes the repeat and payment state through", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimFoundItem: { id: OTHER_CLAIM_ID, repeated: true, ...paidUp },
    })

    const result = await claimNotice("found", "8", contact)

    expect(result).toEqual({ ok: true, repeated: true, claimId: OTHER_CLAIM_ID, ...paidUp })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimFoundItemMutation,
      expect.objectContaining({ id: "8" }),
      { cache: "no-store" },
    )
  })

  it("sends no messengers as null rather than an empty list", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: CLAIM_ID, repeated: false, ...unpaid },
    })

    await claimNotice("lost", "7", { ...contact, socialMedias: [] })

    expect(mocks.graphqlRequest.mock.calls[0]?.[1]).toEqual({
      id: "7",
      contact: { phone: contact.phone, email: contact.email, socialMedias: null },
    })
  })

  it("remembers the claim id for 30 days in a cookie the claim card reads", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: CLAIM_ID, repeated: false, ...unpaid },
    })

    await claimNotice("lost", "7", contact)

    expect(mocks.setCookie).toHaveBeenCalledWith(
      "DERECHI_CLAIM_lost_7",
      CLAIM_ID,
      expect.objectContaining({ maxAge: 30 * 24 * 3600, path: "/", httpOnly: false }),
    )
  })

  it("sets the cookie for a repeat too, so the button stays hidden", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimFoundItem: { id: OTHER_CLAIM_ID, repeated: true, ...paidUp },
    })

    await claimNotice("found", "8", contact)

    expect(mocks.setCookie).toHaveBeenCalledWith(
      "DERECHI_CLAIM_found_8",
      OTHER_CLAIM_ID,
      expect.anything(),
    )
  })

  it.each([
    ["a phone that is not E.164", { ...contact, phone: "0671234567" }],
    ["a malformed email", { ...contact, email: "not-an-email" }],
    ["an unknown messenger", { ...contact, socialMedias: ["SIGNAL"] }],
    ["no contacts at all", undefined],
  ])("rejects %s before calling the API", async (_, values) => {
    expect(await claimNotice("lost", "7", values)).toEqual({ ok: false, reason: "validation" })

    expect(mocks.passesBotCheck).not.toHaveBeenCalled()
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
    expect(mocks.setCookie).not.toHaveBeenCalled()
  })

  it.each([
    ["an unknown kind", "archived", "7"],
    ["an id that would break the cookie name", "lost", "7; Path=/"],
    ["an empty id", "lost", ""],
  ])("rejects %s before calling the API", async (_, kind, id) => {
    // biome-ignore lint/suspicious/noExplicitAny: a server action receives whatever the client posts
    expect(await claimNotice(kind as any, id, contact)).toEqual({
      ok: false,
      reason: "validation",
    })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("stops a bot before the API", async () => {
    mocks.passesBotCheck.mockResolvedValue(false)

    expect(await claimNotice("lost", "7", contact)).toEqual({ ok: false, reason: "captcha" })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("reports a notice that is gone and drops it from the cached pages", async () => {
    mocks.graphqlRequest.mockRejectedValue(notFound())

    expect(await claimNotice("lost", "7", contact)).toEqual({ ok: false, reason: "notFound" })
    expect(mocks.revalidateTag).toHaveBeenCalledWith("item:lost:7", "max")
    expect(mocks.revalidateTag).toHaveBeenCalledWith("items:lost", "max")
    expect(mocks.setCookie).not.toHaveBeenCalled()
  })

  it("reports any other failure without setting the cookie", async () => {
    mocks.graphqlRequest.mockRejectedValue(new mocks.GraphQLRequestError("boom", []))

    expect(await claimNotice("found", "8", contact)).toEqual({ ok: false, reason: "failed" })
    expect(mocks.revalidateTag).not.toHaveBeenCalled()
    expect(mocks.setCookie).not.toHaveBeenCalled()
  })
})

describe("claimStatus", () => {
  it("asks the API about the claim on a lost notice and reports how far the unlock got", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      lostItemClaim: { id: CLAIM_ID, checkoutUrl: CHECKOUT, paid: true, contactsSent: false },
    })

    expect(await claimStatus("lost", "7", CLAIM_ID)).toEqual({
      ok: true,
      checkoutUrl: CHECKOUT,
      paid: true,
      contactsSent: false,
    })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      LostItemClaimQuery,
      { itemId: "7", id: CLAIM_ID },
      { cache: "no-store" },
    )
  })

  it("uses the found query for a found notice and reports no checkout yet as null", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      foundItemClaim: { id: CLAIM_ID, checkoutUrl: null, paid: false, contactsSent: false },
    })

    expect(await claimStatus("found", "8", CLAIM_ID)).toEqual({
      ok: true,
      checkoutUrl: null,
      paid: false,
      contactsSent: false,
    })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      FoundItemClaimQuery,
      { itemId: "8", id: CLAIM_ID },
      { cache: "no-store" },
    )
  })

  it("answers a claim that is not on that notice with ok: false", async () => {
    mocks.graphqlRequest.mockResolvedValue({ lostItemClaim: null })

    expect(await claimStatus("lost", "7", CLAIM_ID)).toEqual({ ok: false })
  })

  it.each([
    ["the old cookie value", "lost", "7", "1x"],
    ["an old token", "lost", "7", OLD_TOKEN],
    ["an old payment code", "lost", "7", "DR-7K3M9Q"],
    ["an id inside other text", "lost", "7", `${CLAIM_ID}; Path=/`],
    ["an empty claim id", "lost", "7", ""],
    ["an unknown kind", "archived", "7", CLAIM_ID],
    ["an item id that would break the cookie name", "lost", "7; Path=/", CLAIM_ID],
  ])("rejects %s before calling the API", async (_, kind, itemId, claimId) => {
    // biome-ignore lint/suspicious/noExplicitAny: a server action receives whatever the client posts
    expect(await claimStatus(kind as any, itemId, claimId)).toEqual({ ok: false })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("rejects a claim id that is not a string", async () => {
    // biome-ignore lint/suspicious/noExplicitAny: a server action receives whatever the client posts
    expect(await claimStatus("lost", "7", 11 as any)).toEqual({ ok: false })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("reports a broken API as ok: false", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("connection refused"))

    expect(await claimStatus("lost", "7", CLAIM_ID)).toEqual({ ok: false })
  })
})

describe("unlockClaim", () => {
  it("prepares the checkout on Fourthwall for a lost notice and returns its link", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      unlockLostItemClaim: {
        id: CLAIM_ID,
        checkoutUrl: CHECKOUT,
        paid: false,
        contactsSent: false,
      },
    })

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({
      ok: true,
      checkoutUrl: CHECKOUT,
      paid: false,
      contactsSent: false,
    })
    expect(mocks.passesBotCheck).toHaveBeenCalled()
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      UnlockLostItemClaimMutation,
      { itemId: "7", id: CLAIM_ID },
      { cache: "no-store" },
    )
  })

  it("uses the found mutation for a found notice", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      unlockFoundItemClaim: {
        id: CLAIM_ID,
        checkoutUrl: CHECKOUT,
        paid: false,
        contactsSent: false,
      },
    })

    expect(await unlockClaim("found", "8", CLAIM_ID)).toEqual(
      expect.objectContaining({ ok: true, checkoutUrl: CHECKOUT }),
    )
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      UnlockFoundItemClaimMutation,
      { itemId: "8", id: CLAIM_ID },
      { cache: "no-store" },
    )
  })

  it("tells the claimant to come back when Fourthwall is rate-limited", async () => {
    mocks.graphqlRequest.mockRejectedValue(
      new mocks.GraphQLRequestError("Fourthwall is busy", [
        { extensions: { classification: "PAYMENT_UNAVAILABLE" } },
      ]),
    )

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({ ok: false, reason: "unavailable" })
  })

  it("reports a claim that is not on that notice as notFound", async () => {
    mocks.graphqlRequest.mockRejectedValue(notFound())

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({ ok: false, reason: "notFound" })
  })

  it("reports any other failure as failed", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("connection refused"))

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({ ok: false, reason: "failed" })
  })

  it("treats a missing checkout link in the answer as a failure", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      unlockLostItemClaim: { id: CLAIM_ID, checkoutUrl: null, paid: false, contactsSent: false },
    })

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({ ok: false, reason: "failed" })
  })

  it("stops a bot before the API", async () => {
    mocks.passesBotCheck.mockResolvedValue(false)

    expect(await unlockClaim("lost", "7", CLAIM_ID)).toEqual({ ok: false, reason: "captcha" })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it.each([
    ["an old token", "lost", "7", OLD_TOKEN],
    ["an old payment code", "lost", "7", "DR-7K3M9Q"],
    ["an id inside other text", "lost", "7", `${CLAIM_ID}; Path=/`],
    ["an empty claim id", "lost", "7", ""],
    ["an unknown kind", "archived", "7", CLAIM_ID],
    ["an empty item id", "lost", "", CLAIM_ID],
  ])("rejects %s before the bot check and the API", async (_, kind, itemId, claimId) => {
    // biome-ignore lint/suspicious/noExplicitAny: a server action receives whatever the client posts
    expect(await unlockClaim(kind as any, itemId, claimId)).toEqual({
      ok: false,
      reason: "validation",
    })
    expect(mocks.passesBotCheck).not.toHaveBeenCalled()
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })
})

describe("confirmReturn", () => {
  const token = "6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11"

  it("confirms the token and refreshes both lists", async () => {
    mocks.graphqlRequest.mockResolvedValue({ confirmReturn: true })

    expect(await confirmReturn(token)).toEqual({ ok: true })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ConfirmReturnMutation,
      { token },
      { cache: "no-store" },
    )
    expect(mocks.revalidateTag).toHaveBeenCalledWith("items:lost", "max")
    expect(mocks.revalidateTag).toHaveBeenCalledWith("items:found", "max")
  })

  it("answers an unknown or spent token with notFound", async () => {
    mocks.graphqlRequest.mockRejectedValue(notFound())

    expect(await confirmReturn(token)).toEqual({ ok: false, reason: "notFound" })
    expect(mocks.revalidateTag).not.toHaveBeenCalled()
  })

  it.each([
    ["an empty token", ""],
    ["an oversized token", "x".repeat(65)],
  ])("treats %s as not found without calling the API", async (_, value) => {
    expect(await confirmReturn(value)).toEqual({ ok: false, reason: "notFound" })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("reports a broken API as a failure, not as an invalid link", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("connection refused"))

    expect(await confirmReturn(token)).toEqual({ ok: false, reason: "failed" })
    expect(mocks.revalidateTag).not.toHaveBeenCalled()
  })
})
