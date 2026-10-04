import { describe, expect, it } from "vitest"
import { claimCookieName, hasClaimCookie, readClaimCookie } from "./claim-cookie"

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

  it("reads the claim id stored for this notice", () => {
    const header = "DERECHI_CLAIM_lost_17=12; DERECHI_CLAIM_lost_7=11; DERECHI_LOCALE=uk"

    expect(readClaimCookie(header, "lost", "7")).toBe("11")
    expect(readClaimCookie(header, "lost", "17")).toBe("12")
  })

  it("returns the old value of a cookie set before claim ids", () => {
    expect(readClaimCookie("DERECHI_CLAIM_found_7=1", "found", "7")).toBe("1")
    expect(
      readClaimCookie("DERECHI_CLAIM_found_7=6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11", "found", "7"),
    ).toBe("6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11")
  })

  it("returns null when this notice has no cookie", () => {
    expect(readClaimCookie("DERECHI_CLAIM_found_7=11", "lost", "7")).toBeNull()
    expect(readClaimCookie("", "lost", "7")).toBeNull()
  })
})
