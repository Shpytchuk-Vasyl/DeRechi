import { describe, expect, it } from "vitest"
import { claimCookieName, hasClaimCookie } from "./claim-cookie"

describe("claim cookie", () => {
  it("names the cookie after the notice kind and id", () => {
    expect(claimCookieName("found", "42")).toBe("DERECHI_CLAIM_found_42")
  })

  it("finds the cookie anywhere in the header", () => {
    const header = "DERECHI_LOCALE=uk; DERECHI_CLAIM_lost_7=1; DERECHI_COUNTRY=PL"

    expect(hasClaimCookie(header, "lost", "7")).toBe(true)
  })

  it("does not mistake another notice's cookie for this one", () => {
    const header = "DERECHI_CLAIM_lost_17=1; DERECHI_CLAIM_found_7=1"

    expect(hasClaimCookie(header, "lost", "7")).toBe(false)
    expect(hasClaimCookie(header, "lost", "1")).toBe(false)
  })

  it("is absent from an empty header", () => {
    expect(hasClaimCookie("", "lost", "7")).toBe(false)
  })
})
