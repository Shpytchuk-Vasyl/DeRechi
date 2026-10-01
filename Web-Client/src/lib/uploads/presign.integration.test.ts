import { describe, expect, it } from "vitest"
import { presignUrl } from "./presign"

const endpoint = process.env.S3_TEST_ENDPOINT
const describeIf = endpoint ? describe : describe.skip

describeIf(`presigned upload against ${endpoint}`, () => {
  const target = {
    endpoint: endpoint ?? "",
    bucket: process.env.S3_BUCKET ?? "derechi-files",
    region: process.env.S3_REGION ?? "us-east-1",
    accessKeyId: process.env.S3_ACCESS_KEY ?? "derechi",
    secretAccessKey: process.env.S3_SECRET_KEY ?? "derechi123",
    expiresIn: 300,
  }

  it("accepts a PUT and serves the object back", async () => {
    const key = `items/test/${crypto.randomUUID()}.png`
    const body = Buffer.from("not really a png, but bytes are bytes")

    const url = presignUrl({
      ...target,
      key,
      method: "PUT",
      contentType: "image/png",
      contentLength: body.byteLength,
    })
    const upload = await fetch(url, {
      method: "PUT",
      body,
      headers: { "Content-Type": "image/png" },
    })

    expect(upload.status, await upload.text()).toBe(200)

    const readBase = process.env.S3_TEST_READ_URL ?? `${target.endpoint}/${target.bucket}`
    const download = await fetch(`${readBase}/${key}`)
    expect(download.status).toBe(200)
    expect(await download.text()).toBe(body.toString())
  })

  const itThroughGateway = process.env.S3_TEST_MAX_BYTES ? it : it.skip

  itThroughGateway("rejects a body over the Gateway's limit", async () => {
    const limit = Number(process.env.S3_TEST_MAX_BYTES)
    const url = presignUrl({
      ...target,
      key: `items/test/${crypto.randomUUID()}.png`,
      method: "PUT",
    })

    const response = await fetch(url, {
      method: "PUT",
      body: Buffer.alloc(limit + 1024 * 1024, 1),
      headers: { "Content-Type": "image/png" },
    })

    expect(response.status).toBe(413)
  })

  it("rejects a PUT whose Content-Type differs from the signed one", async () => {
    const body = Buffer.from("<script>alert(1)</script>")
    const url = presignUrl({
      ...target,
      key: `items/test/${crypto.randomUUID()}.png`,
      method: "PUT",
      contentType: "image/png",
      contentLength: body.byteLength,
    })

    const response = await fetch(url, {
      method: "PUT",
      body,
      headers: { "Content-Type": "text/html" },
    })
    expect(response.status).toBe(403)
  })

  it("rejects a PUT whose body is not the signed length", async () => {
    const url = presignUrl({
      ...target,
      key: `items/test/${crypto.randomUUID()}.png`,
      method: "PUT",
      contentType: "image/png",
      contentLength: 3,
    })

    const response = await fetch(url, {
      method: "PUT",
      body: Buffer.alloc(4),
      headers: { "Content-Type": "image/png" },
    })
    expect(response.status).toBe(403)
  })

  it("rejects a tampered key", async () => {
    const url = presignUrl({ ...target, key: "items/test/signed.png", method: "PUT" })
    const tampered = url.replace("signed.png", "someone-elses.png")

    const response = await fetch(tampered, { method: "PUT", body: "x" })
    expect(response.status).toBe(403)
  })
})
