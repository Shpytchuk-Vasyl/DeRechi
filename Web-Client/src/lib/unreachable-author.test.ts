import { describe, expect, it } from "vitest"
import { unreachableAuthor } from "./unreachable-author"

const extensions = {
  classification: "AUTHOR_UNREACHABLE",
  socialMedia: "TELEGRAM",
  nickName: "@olena_lviv",
  profileUrl: "https://t.me/olena_lviv",
}

describe("unreachableAuthor", () => {
  it("reads the network, the nickname and the profile link of the error", () => {
    expect(unreachableAuthor([{ extensions }])).toEqual({
      socialMedia: "TELEGRAM",
      nickName: "@olena_lviv",
      profileUrl: "https://t.me/olena_lviv",
    })
  })

  it("accepts Facebook, whose nickname is a display name", () => {
    expect(
      unreachableAuthor([
        {
          extensions: {
            ...extensions,
            socialMedia: "FACEBOOK",
            nickName: "Олена Коваль",
            profileUrl: "https://www.facebook.com/olena.koval",
          },
        },
      ]),
    ).toEqual({
      socialMedia: "FACEBOOK",
      nickName: "Олена Коваль",
      profileUrl: "https://www.facebook.com/olena.koval",
    })
  })

  it("finds the error among others", () => {
    expect(
      unreachableAuthor([{ extensions: { classification: "BAD_REQUEST" } }, { extensions }])
        ?.nickName,
    ).toBe("@olena_lviv")
  })

  it.each([
    ["missing", undefined],
    ["null", null],
  ])("has no profile link when the link is %s", (_, profileUrl) => {
    expect(
      unreachableAuthor([{ extensions: { ...extensions, profileUrl } }])?.profileUrl,
    ).toBeNull()
  })

  it.each([
    ["a script", "javascript:alert(1)"],
    ["plain http", "http://t.me/olena_lviv"],
    ["not a link", "t.me/olena_lviv"],
    ["not a string", 42],
  ])("drops a profile link that is %s", (_, profileUrl) => {
    expect(unreachableAuthor([{ extensions: { ...extensions, profileUrl } }])).toEqual({
      socialMedia: "TELEGRAM",
      nickName: "@olena_lviv",
      profileUrl: null,
    })
  })

  it.each([
    ["no nickname", { ...extensions, nickName: undefined }],
    ["a blank nickname", { ...extensions, nickName: "  " }],
    ["an unknown network", { ...extensions, socialMedia: "MYSPACE" }],
    ["no network", { ...extensions, socialMedia: null }],
  ])("is null for %s", (_, value) => {
    expect(unreachableAuthor([{ extensions: value }])).toBeNull()
  })

  it.each([
    ["another classification", [{ extensions: { ...extensions, classification: "NOT_FOUND" } }]],
    ["no extensions", [{}]],
    ["no errors", []],
  ])("is null for %s", (_, errors) => {
    expect(unreachableAuthor(errors)).toBeNull()
  })
})
