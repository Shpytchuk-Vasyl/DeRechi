import { beforeEach, describe, expect, it, vi } from "vitest"
import {
  ClaimByTokenQuery,
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
  UnlockClaimMutation,
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

const TOKEN = "6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11"
const OTHER_TOKEN = "0b9a3c1d-5e2f-4a6b-8c7d-9e0f1a2b3c4d"
const CHECKOUT = "https://derechi-shop.fourthwall.com/cart/checkout?products=var_1:1"

const unpaid = { token: TOKEN, checkoutUrl: null, paid: false, contactsSent: false }
const paidUp = { token: OTHER_TOKEN, checkoutUrl: CHECKOUT, paid: true, contactsSent: true }

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
      claimLostItem: { id: "11", repeated: false, ...unpaid },
    })

    const result = await claimNotice("lost", "7", contact)

    expect(result).toEqual({ ok: true, repeated: false, ...unpaid })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimLostItemMutation,
      { id: "7", contact },
      { revalidate: false },
    )
  })

  it("uses the found mutation for a found notice and passes the repeat and payment state through", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimFoundItem: { id: "12", repeated: true, ...paidUp },
    })

    const result = await claimNotice("found", "8", contact)

    expect(result).toEqual({ ok: true, repeated: true, ...paidUp })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimFoundItemMutation,
      expect.objectContaining({ id: "8" }),
      { revalidate: false },
    )
  })

  it("sends no messengers as null rather than an empty list", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: "11", repeated: false, ...unpaid },
    })

    await claimNotice("lost", "7", { ...contact, socialMedias: [] })

    expect(mocks.graphqlRequest.mock.calls[0]?.[1]).toEqual({
      id: "7",
      contact: { phone: contact.phone, email: contact.email, socialMedias: null },
    })
  })

  it("remembers the token for 30 days in a cookie the claim card reads", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: "11", repeated: false, ...unpaid },
    })

    await claimNotice("lost", "7", contact)

    expect(mocks.setCookie).toHaveBeenCalledWith(
      "DERECHI_CLAIM_lost_7",
      TOKEN,
      expect.objectContaining({ maxAge: 30 * 24 * 3600, path: "/", httpOnly: false }),
    )
  })

  it("sets the cookie for a repeat too, so the button stays hidden", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimFoundItem: { id: "12", repeated: true, ...paidUp },
    })

    await claimNotice("found", "8", contact)

    expect(mocks.setCookie).toHaveBeenCalledWith(
      "DERECHI_CLAIM_found_8",
      OTHER_TOKEN,
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
  it("asks the API about the token and reports the payment state", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claim: { id: "11", checkoutUrl: CHECKOUT, paid: true, contactsSent: false },
    })

    expect(await claimStatus(TOKEN)).toEqual({
      ok: true,
      checkoutUrl: CHECKOUT,
      paid: true,
      contactsSent: false,
    })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimByTokenQuery,
      { token: TOKEN },
      { revalidate: false },
    )
  })

  it("reports no checkout yet as null", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claim: { id: "11", checkoutUrl: null, paid: false, contactsSent: false },
    })

    expect(await claimStatus(TOKEN)).toEqual({
      ok: true,
      checkoutUrl: null,
      paid: false,
      contactsSent: false,
    })
  })

  it("answers an unknown token with ok: false", async () => {
    mocks.graphqlRequest.mockResolvedValue({ claim: null })

    expect(await claimStatus(TOKEN)).toEqual({ ok: false })
  })

  it.each([
    ["the old cookie value", "1"],
    ["an old payment code", "DR-7K3M9Q"],
    ["an upper-case token", TOKEN.toUpperCase()],
    ["a short token", TOKEN.slice(0, -1)],
    ["a token inside other text", `${TOKEN}; Path=/`],
    ["an empty string", ""],
  ])("rejects %s before calling the API", async (_, value) => {
    expect(await claimStatus(value)).toEqual({ ok: false })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("rejects a value that is not a string", async () => {
    // biome-ignore lint/suspicious/noExplicitAny: a server action receives whatever the client posts
    expect(await claimStatus(42 as any)).toEqual({ ok: false })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("reports a broken API as ok: false", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("connection refused"))

    expect(await claimStatus(TOKEN)).toEqual({ ok: false })
  })
})

describe("unlockClaim", () => {
  it("prepares the checkout on Fourthwall and returns its link", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      unlockClaim: { id: "11", checkoutUrl: CHECKOUT, paid: false, contactsSent: false },
    })

    expect(await unlockClaim(TOKEN)).toEqual({
      ok: true,
      checkoutUrl: CHECKOUT,
      paid: false,
      contactsSent: false,
    })
    expect(mocks.passesBotCheck).toHaveBeenCalled()
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      UnlockClaimMutation,
      { token: TOKEN },
      { revalidate: false },
    )
  })

  it("tells the claimant to come back when Fourthwall is rate-limited", async () => {
    mocks.graphqlRequest.mockRejectedValue(
      new mocks.GraphQLRequestError("Fourthwall is busy", [
        { extensions: { classification: "PAYMENT_UNAVAILABLE" } },
      ]),
    )

    expect(await unlockClaim(TOKEN)).toEqual({ ok: false, reason: "unavailable" })
  })

  it("reports an unknown token as notFound", async () => {
    mocks.graphqlRequest.mockRejectedValue(notFound())

    expect(await unlockClaim(TOKEN)).toEqual({ ok: false, reason: "notFound" })
  })

  it("reports any other failure as failed", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("connection refused"))

    expect(await unlockClaim(TOKEN)).toEqual({ ok: false, reason: "failed" })
  })

  it("treats a missing checkout link in the answer as a failure", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      unlockClaim: { id: "11", checkoutUrl: null, paid: false, contactsSent: false },
    })

    expect(await unlockClaim(TOKEN)).toEqual({ ok: false, reason: "failed" })
  })

  it("stops a bot before the API", async () => {
    mocks.passesBotCheck.mockResolvedValue(false)

    expect(await unlockClaim(TOKEN)).toEqual({ ok: false, reason: "captcha" })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it.each([
    ["the old cookie value", "1"],
    ["an old payment code", "DR-7K3M9Q"],
    ["a token inside other text", `${TOKEN}; Path=/`],
    ["an empty string", ""],
  ])("rejects %s before the bot check and the API", async (_, value) => {
    expect(await unlockClaim(value)).toEqual({ ok: false, reason: "validation" })
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
      { revalidate: false },
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
