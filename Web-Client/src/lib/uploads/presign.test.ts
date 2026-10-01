import { describe, expect, it } from "vitest"
import { presignUrl } from "./presign"

const base = {
  endpoint: "http://localhost:8080",
  bucket: "derechi-files",
  key: "items/2026/09/photo.png",
  method: "PUT" as const,
  expiresIn: 300,
  accessKeyId: "derechi",
  secretAccessKey: "derechi123",
  region: "us-east-1",
  now: new Date("2026-09-29T12:00:00Z"),
}

function signedHeadersOf(url: string): string | null {
  return new URL(url).searchParams.get("X-Amz-SignedHeaders")
}

function signatureOf(url: string): string | null {
  return new URL(url).searchParams.get("X-Amz-Signature")
}

describe("presignUrl", () => {
  it("signs only the host when no headers are pinned", () => {
    expect(signedHeadersOf(presignUrl(base))).toBe("host")
  })

  it("pins Content-Type and Content-Length into the signed headers, sorted", () => {
    const url = presignUrl({ ...base, contentType: "image/png", contentLength: 1234 })
    expect(signedHeadersOf(url)).toBe("content-length;content-type;host")
  })

  it("produces a different signature for a different Content-Type", () => {
    const png = presignUrl({ ...base, contentType: "image/png" })
    const html = presignUrl({ ...base, contentType: "text/html" })
    expect(signatureOf(png)).not.toBe(signatureOf(html))
  })

  it("produces a different signature for a different Content-Length", () => {
    const small = presignUrl({ ...base, contentType: "image/png", contentLength: 10 })
    const large = presignUrl({ ...base, contentType: "image/png", contentLength: 11 })
    expect(signatureOf(small)).not.toBe(signatureOf(large))
  })

  it("is deterministic for the same input", () => {
    const input = { ...base, contentType: "image/png", contentLength: 10 }
    expect(presignUrl(input)).toBe(presignUrl(input))
  })
})
