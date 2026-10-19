import { describe, expect, it } from "vitest"
import { qrDataUrl, qrModules, qrSvg } from "./qr"

const URL = "https://www.derechi.site/uk/lost/12345"

function finderAt(modules: boolean[][], top: number, left: number): boolean {
  for (let row = 0; row < 7; row++) {
    for (let col = 0; col < 7; col++) {
      const ring = row === 0 || row === 6 || col === 0 || col === 6
      const core = row >= 2 && row <= 4 && col >= 2 && col <= 4
      if (modules[top + row][left + col] !== (ring || core)) return false
    }
  }
  return true
}

describe("qrModules", () => {
  it("fits a notice link into a small square code", () => {
    const modules = qrModules(URL)
    expect(modules).toHaveLength(29)
    for (const row of modules) expect(row).toHaveLength(29)
  })

  it("has the three finder patterns and none in the fourth corner", () => {
    const modules = qrModules(URL)
    const end = modules.length - 7
    expect(finderAt(modules, 0, 0)).toBe(true)
    expect(finderAt(modules, 0, end)).toBe(true)
    expect(finderAt(modules, end, 0)).toBe(true)
    expect(finderAt(modules, end, end)).toBe(false)
  })

  it("encodes different links differently and the same link the same way", () => {
    expect(qrModules(URL)).toEqual(qrModules(URL))
    expect(qrModules(URL)).not.toEqual(qrModules(`${URL}6`))
  })
})

describe("qrSvg", () => {
  it("draws every dark module once, inside a white quiet zone", () => {
    const modules = qrModules(URL)
    const dark = modules.flat().filter(Boolean).length
    const svg = qrSvg(URL, "#3a2e5c")

    expect(svg).toContain('viewBox="0 0 37 37"')
    expect(svg).toContain('fill="#ffffff"')
    expect(svg).toContain('fill="#3a2e5c"')
    expect(svg.match(/h1v1h-1z/g)).toHaveLength(dark)
    expect(svg).toContain("M4 4h1v1h-1z")
  })

  it("wraps the drawing in a data URL an image tag can load", () => {
    const url = qrDataUrl(URL)
    expect(url.startsWith("data:image/svg+xml;base64,")).toBe(true)
    expect(Buffer.from(url.split(",")[1], "base64").toString()).toBe(qrSvg(URL))
  })
})
