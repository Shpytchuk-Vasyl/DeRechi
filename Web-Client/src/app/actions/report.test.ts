import { beforeEach, describe, expect, it, vi } from "vitest"
import { CreateFoundItemMutation, CreateLostItemMutation } from "@/graphql/documents"
import { todayIso } from "@/lib/intl/dates"

const mocks = vi.hoisted(() => ({
  graphqlRequest: vi.fn(),
  passesBotCheck: vi.fn(),
  revalidateTag: vi.fn(),
}))

vi.mock("@/graphql/client", () => ({ graphqlRequest: mocks.graphqlRequest }))
vi.mock("@/lib/bot-check", () => ({ passesBotCheck: mocks.passesBotCheck }))
vi.mock("next/cache", () => ({ revalidateTag: mocks.revalidateTag }))

const { createNotice } = await import("./report")

const notice = {
  title: "Brown leather wallet",
  description: "",
  date: todayIso(-1),
  compensation: 500,
  currency: "PLN",
  image: "items/2026/09/abc.jpg",
  categoryId: "4",
  place: { id: "ChIJ123", name: "Kraków, Rynek", lat: 50.06, lon: 19.94, countryCode: "PL" },
  contact: { phone: "+48501234567", email: "olena@example.com", socialMedias: [] },
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, "log").mockImplementation(() => {})
  mocks.passesBotCheck.mockResolvedValue(true)
})

describe("createNotice", () => {
  it("creates a lost notice from the normalised ItemInput and returns its id", async () => {
    mocks.graphqlRequest.mockResolvedValue({ createLostItem: { id: "31" } })

    expect(await createNotice("lost", notice)).toEqual({ ok: true, id: "31" })

    const [document, variables, options] = mocks.graphqlRequest.mock.calls[0] ?? []
    expect(document).toBe(CreateLostItemMutation)
    expect(options).toEqual({ revalidate: false })
    expect(variables.input).toMatchObject({
      title: "Brown leather wallet",
      description: null,
      compensation: { amount: 500, currency: "PLN" },
      contact: { socialMedias: null },
    })
  })

  it("creates a found notice through the found mutation", async () => {
    mocks.graphqlRequest.mockResolvedValue({ createFoundItem: { id: "32" } })

    expect(await createNotice("found", notice)).toEqual({ ok: true, id: "32" })
    expect(mocks.graphqlRequest.mock.calls[0]?.[0]).toBe(CreateFoundItemMutation)
  })

  it("refreshes the list and the place suggestions after a new notice", async () => {
    mocks.graphqlRequest.mockResolvedValue({ createLostItem: { id: "31" } })

    await createNotice("lost", notice)

    expect(mocks.revalidateTag).toHaveBeenCalledWith("items:lost", "max")
    expect(mocks.revalidateTag).toHaveBeenCalledWith("places", "max")
  })

  it("rejects a found notice without a photo before calling the API", async () => {
    const { image, ...withoutImage } = notice
    expect(image).toBeTruthy()

    expect(await createNotice("found", withoutImage)).toEqual({ ok: false, reason: "validation" })
    expect(mocks.passesBotCheck).not.toHaveBeenCalled()
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("stops a bot before the API", async () => {
    mocks.passesBotCheck.mockResolvedValue(false)

    expect(await createNotice("lost", notice)).toEqual({ ok: false, reason: "captcha" })
    expect(mocks.graphqlRequest).not.toHaveBeenCalled()
  })

  it("reports a rejected mutation as a failure and refreshes nothing", async () => {
    mocks.graphqlRequest.mockRejectedValue(new Error("Unsupported country: PL"))

    expect(await createNotice("lost", notice)).toEqual({ ok: false, reason: "failed" })
    expect(mocks.revalidateTag).not.toHaveBeenCalled()
  })
})
