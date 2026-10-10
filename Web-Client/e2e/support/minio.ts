import { readFileSync } from "node:fs"
import path from "node:path"
import { presignUrl } from "../../src/lib/uploads/presign"
import { env, secrets } from "./env"

export const PHOTO_FIXTURE = path.resolve(__dirname, "../fixtures/files/photo.png")

export function photoKey(token: string, suffix = ""): string {
  return `items/e2e/${token}${suffix}.png`
}

export function missingPhotoKey(token: string): string {
  return photoKey(token, "-missing")
}

export async function uploadPhoto(
  key: string,
  body: Buffer = readFileSync(PHOTO_FIXTURE),
  contentType = "image/png",
): Promise<string> {
  const endpoint = env.s3.publicEndpoint
  if (!endpoint) throw new Error("e2e: S3_PUBLIC_ENDPOINT is not set in .env.local")

  const url = presignUrl({
    endpoint,
    bucket: env.s3.bucket,
    region: env.s3.region,
    key,
    method: "PUT",
    contentType,
    contentLength: body.byteLength,
    expiresIn: env.s3.expiresIn,
    accessKeyId: secrets.s3AccessKey,
    secretAccessKey: secrets.s3SecretKey,
  })

  const response = await fetch(url, {
    method: "PUT",
    body: new Uint8Array(body),
    headers: { "Content-Type": contentType },
  })
  if (!response.ok) {
    throw new Error(`e2e: photo upload of ${key} failed with ${response.status}`)
  }
  return key
}

export function photoUrl(key: string): string {
  if (!env.filesURL) throw new Error("e2e: NEXT_PUBLIC_FILES_URL is not set in .env.local")
  return `${env.filesURL.replace(/\/+$/, "")}/${key}`
}
