// Regenerates src/assets/3d/*.png: 3dicons renders recoloured into the site palette.
//
//   node scripts/recolor-3d.mjs
//
// Each render's brightness is run through a gradient map (dark shade → accent → near white),
// so the render keeps all of its lighting while the hue becomes one of ours. The source is the
// "gradient" style, not "clay": it has the widest tonal range, which is what the map feeds on.
// Change a ramp below and rerun: nothing is recoloured at runtime.

import { writeFile } from "node:fs/promises"
import { deflateSync, inflateSync } from "node:zlib"

const CDN = "https://3dicons.sgp1.cdn.digitaloceanspaces.com/v1/dynamic/gradient"
const OUT = new URL("../src/assets/3d/", import.meta.url)

const RAMPS = {
  zoom: ["#4a3590", "#8f6ae8", "#c9a8ff", "#f4ecff"], // --purple
  key: ["#8a5a12", "#e0a93a", "#ffe58a", "#fffbea"], // --yellow
  wallet: ["#1f5b45", "#4fbf95", "#a8f0d0", "#effdf6"], // --mint
  bag: ["#8f3358", "#e57aa6", "#ffb3d1", "#fff0f6"], // --pink
  "file-text": ["#2a4f8f", "#5e95e8", "#9ec8ff", "#eef6ff"], // --blue
}
const STOPS = [0, 0.4, 0.75, 1]

const hex = (h) => [1, 3, 5].map((i) => Number.parseInt(h.slice(i, i + 2), 16))
const luma = (r, g, b) => 0.2126 * r + 0.7152 * g + 0.0722 * b

function gradientMap(colours) {
  const stops = colours.map((c, i) => [STOPS[i], hex(c)])
  return (t) => {
    const v = Math.min(1, Math.max(0, t))
    let i = 0
    while (i < stops.length - 2 && v > stops[i + 1][0]) i++
    const [p0, c0] = stops[i]
    const [p1, c1] = stops[i + 1]
    const k = (v - p0) / (p1 - p0)
    return c0.map((c, j) => c + (c1[j] - c) * k)
  }
}

function decode(buf) {
  let pos = 8
  let width = 0
  let height = 0
  const idat = []
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos)
    const name = buf.toString("ascii", pos + 4, pos + 8)
    const data = buf.subarray(pos + 8, pos + 8 + len)
    if (name === "IHDR") {
      width = data.readUInt32BE(0)
      height = data.readUInt32BE(4)
      if (data[8] !== 8 || data[9] !== 6 || data[12] !== 0) throw new Error("expected RGBA8")
    }
    if (name === "IDAT") idat.push(data)
    pos += 12 + len
  }
  const raw = inflateSync(Buffer.concat(idat))
  const stride = width * 4
  const out = Buffer.alloc(stride * height)
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]
    for (let i = 0; i < stride; i++) {
      const at = y * stride + i
      const a = i >= 4 ? out[at - 4] : 0
      const b = y > 0 ? out[at - stride] : 0
      const c = i >= 4 && y > 0 ? out[at - stride - 4] : 0
      let p = 0
      if (filter === 1) p = a
      else if (filter === 2) p = b
      else if (filter === 3) p = (a + b) >> 1
      else if (filter === 4) {
        const pa = Math.abs(b - c)
        const pb = Math.abs(a - c)
        const pc = Math.abs(a + b - 2 * c)
        p = pa <= pb && pa <= pc ? a : pb <= pc ? b : c
      }
      out[at] = (raw[y * (stride + 1) + 1 + i] + p) & 255
    }
  }
  return { width, height, data: out }
}

const CRC = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

function chunk(name, data) {
  const body = Buffer.concat([Buffer.from(name, "ascii"), data])
  let crc = 0xffffffff
  for (const byte of body) crc = CRC[(crc ^ byte) & 255] ^ (crc >>> 8)
  const head = Buffer.alloc(4)
  head.writeUInt32BE(data.length)
  const tail = Buffer.alloc(4)
  tail.writeUInt32BE((crc ^ 0xffffffff) >>> 0)
  return Buffer.concat([head, body, tail])
}

function encode({ width, height, data }) {
  const stride = width * 4
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++)
    data.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ])
}

for (const [name, colours] of Object.entries(RAMPS)) {
  const response = await fetch(`${CDN}/${name}-dynamic-gradient.png`)
  if (!response.ok) throw new Error(`${name}: HTTP ${response.status}`)
  const image = decode(Buffer.from(await response.arrayBuffer()))
  const { data } = image

  const levels = []
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] > 200) levels.push(luma(data[i], data[i + 1], data[i + 2]))
  }
  levels.sort((a, b) => a - b)
  const lo = levels[Math.floor(levels.length * 0.02)]
  const hi = levels[Math.floor(levels.length * 0.98)]

  const map = gradientMap(colours)
  for (let i = 0; i < data.length; i += 4) {
    const [r, g, b] = map((luma(data[i], data[i + 1], data[i + 2]) - lo) / (hi - lo))
    data[i] = r
    data[i + 1] = g
    data[i + 2] = b
  }

  await writeFile(new URL(`${name}.png`, OUT), encode(image))
  console.log(`${name}.png`)
}
