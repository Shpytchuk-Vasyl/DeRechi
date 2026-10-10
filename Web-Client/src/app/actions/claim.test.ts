import { beforeEach, describe, expect, it, vi } from "vitest"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  ConfirmReturnMutation,
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

    get isDisposableEmail(): boolean {
      return this.errors.some((error) => error.extensions?.classification === "DISPOSABLE_EMAIL")
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

const { claimNotice, confirmReturn } = await import("./claim")

const contact = {
  phone: "+48501234567",
  email: "claimant@example.com",
  socialMedias: ["VIBER", "WHATSAPP"],
}

const CLAIM_ID = "11"
const OTHER_CLAIM_ID = "12"

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
      claimLostItem: { id: CLAIM_ID, repeated: false },
    })

    const result = await claimNotice("lost", "7", contact)

    expect(result).toEqual({ ok: true, repeated: false, claimId: CLAIM_ID })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimLostItemMutation,
      { id: "7", contact },
      { cache: "no-store" },
    )
  })

  it("uses the found mutation for a found notice and passes the repeat through", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimFoundItem: { id: OTHER_CLAIM_ID, repeated: true },
    })

    const result = await claimNotice("found", "8", contact)

    expect(result).toEqual({ ok: true, repeated: true, claimId: OTHER_CLAIM_ID })
    expect(mocks.graphqlRequest).toHaveBeenCalledWith(
      ClaimFoundItemMutation,
      expect.objectContaining({ id: "8" }),
      { cache: "no-store" },
    )
  })

  it("sends no messengers as null rather than an empty list", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: CLAIM_ID, repeated: false },
    })

    await claimNotice("lost", "7", { ...contact, socialMedias: [] })

    expect(mocks.graphqlRequest.mock.calls[0]?.[1]).toEqual({
      id: "7",
      contact: { phone: contact.phone, email: contact.email, socialMedias: null },
    })
  })

  it("remembers the claim id for 30 days in a cookie the claim card reads", async () => {
    mocks.graphqlRequest.mockResolvedValue({
      claimLostItem: { id: CLAIM_ID, repeated: false },
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
      claimFoundItem: { id: OTHER_CLAIM_ID, repeated: true },
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

  it("tells a throwaway email apart, so the form can mark the field", async () => {
    mocks.graphqlRequest.mockRejectedValue(
      new mocks.GraphQLRequestError("email: Disposable email addresses are not accepted", [
        { extensions: { classification: "DISPOSABLE_EMAIL" } },
      ]),
    )

    expect(await claimNotice("lost", "7", contact)).toEqual({
      ok: false,
      reason: "disposableEmail",
    })
    expect(mocks.setCookie).not.toHaveBeenCalled()
  })

  it("reports any other failure without setting the cookie", async () => {
    mocks.graphqlRequest.mockRejectedValue(new mocks.GraphQLRequestError("boom", []))

    expect(await claimNotice("found", "8", contact)).toEqual({ ok: false, reason: "failed" })
    expect(mocks.revalidateTag).not.toHaveBeenCalled()
    expect(mocks.setCookie).not.toHaveBeenCalled()
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
