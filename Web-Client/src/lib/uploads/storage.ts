import "server-only"
import { randomUUID } from "node:crypto"
import { serverEnv } from "@/lib/env/server"
import { presignUrl } from "@/lib/uploads/presign"

export const ALLOWED_IMAGE_TYPES: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "image/gif": "gif",
}

export const MAX_UPLOAD_BYTES = serverEnv.S3_MAX_UPLOAD_BYTES

export type UploadTarget = {
  key: string
  url: string
  expiresIn: number
}

/** items/2026/09/<uuid>.jpg */
function newKey(extension: string): string {
  const now = new Date()
  const month = String(now.getUTCMonth() + 1).padStart(2, "0")
  return `items/${now.getUTCFullYear()}/${month}/${randomUUID()}.${extension}`
}

export function contentTypeForKey(key: string): string | undefined {
  const extension = key.slice(key.lastIndexOf(".") + 1).toLowerCase()
  return Object.keys(ALLOWED_IMAGE_TYPES).find((type) => ALLOWED_IMAGE_TYPES[type] === extension)
}

export function createUploadTarget(contentType: string, size: number): UploadTarget {
  const extension = ALLOWED_IMAGE_TYPES[contentType]
  if (!extension) {
    throw new Error(`Unsupported content type: ${contentType}`)
  }

  const key = newKey(extension)
  const expiresIn = serverEnv.S3_UPLOAD_EXPIRES_SECONDS

  return {
    key,
    expiresIn,
    url: presignUrl({
      endpoint: serverEnv.S3_PUBLIC_ENDPOINT,
      bucket: serverEnv.S3_BUCKET,
      key,
      method: "PUT",
      contentType,
      contentLength: size,
      expiresIn,
      accessKeyId: serverEnv.S3_ACCESS_KEY,
      secretAccessKey: serverEnv.S3_SECRET_KEY,
      region: serverEnv.S3_REGION,
    }),
  }
}
