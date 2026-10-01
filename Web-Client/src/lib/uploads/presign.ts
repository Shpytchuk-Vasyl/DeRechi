import { createHash, createHmac } from "node:crypto"

const ALGORITHM = "AWS4-HMAC-SHA256"
const SERVICE = "s3"
const UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

export type PresignInput = {
  endpoint: string
  bucket: string
  key: string
  method?: "PUT" | "GET"
  expiresIn: number
  accessKeyId: string
  secretAccessKey: string
  region: string
  now?: Date
  contentType?: string
  contentLength?: number
}

function rfc3986(value: string): string {
  return encodeURIComponent(value).replace(
    /[!'()*]/g,
    (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`,
  )
}

function encodePath(path: string): string {
  return path.split("/").map(rfc3986).join("/")
}

function sha256(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex")
}

function hmac(key: Buffer | string, value: string): Buffer {
  return createHmac("sha256", key).update(value, "utf8").digest()
}

function timestamps(now: Date): { amzDate: string; dateStamp: string } {
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, "")
  return { amzDate, dateStamp: amzDate.slice(0, 8) }
}

export function presignUrl(input: PresignInput): string {
  const endpoint = input.endpoint.replace(/\/+$/, "")
  const host = new URL(endpoint).host
  const { amzDate, dateStamp } = timestamps(input.now ?? new Date())
  const credentialScope = `${dateStamp}/${input.region}/${SERVICE}/aws4_request`

  const canonicalUri = encodePath(`/${input.bucket}/${input.key}`)

  const headers: Record<string, string> = { host }
  if (input.contentType !== undefined) {
    headers["content-type"] = input.contentType.trim()
  }
  if (input.contentLength !== undefined) {
    headers["content-length"] = String(input.contentLength)
  }
  const headerNames = Object.keys(headers).sort()
  const canonicalHeaders = headerNames.map((name) => `${name}:${headers[name]}\n`).join("")
  const signedHeaders = headerNames.join(";")

  const query: Record<string, string> = {
    "X-Amz-Algorithm": ALGORITHM,
    "X-Amz-Credential": `${input.accessKeyId}/${credentialScope}`,
    "X-Amz-Date": amzDate,
    "X-Amz-Expires": String(input.expiresIn),
    "X-Amz-SignedHeaders": signedHeaders,
  }

  const canonicalQuery = Object.keys(query)
    .sort()
    .map((name) => `${rfc3986(name)}=${rfc3986(query[name])}`)
    .join("&")

  const canonicalRequest = [
    input.method ?? "PUT",
    canonicalUri,
    canonicalQuery,
    canonicalHeaders,
    signedHeaders,
    UNSIGNED_PAYLOAD,
  ].join("\n")

  const stringToSign = [ALGORITHM, amzDate, credentialScope, sha256(canonicalRequest)].join("\n")

  const signingKey = [dateStamp, input.region, SERVICE, "aws4_request"].reduce<Buffer>(
    (key, part) => hmac(key, part),
    Buffer.from(`AWS4${input.secretAccessKey}`, "utf8"),
  )

  const signature = hmac(signingKey, stringToSign).toString("hex")

  return `${endpoint}${canonicalUri}?${canonicalQuery}&X-Amz-Signature=${signature}`
}
