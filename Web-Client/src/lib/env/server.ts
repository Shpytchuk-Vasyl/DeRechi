import "server-only"
import * as z from "zod"

const schema = z.object({
  GRAPHQL_URL: z.url(),

  FILES_URL: z.url(),

  S3_PUBLIC_ENDPOINT: z.url(),
  S3_BUCKET: z.string(),
  S3_REGION: z.string(),
  S3_ACCESS_KEY: z.string(),
  S3_SECRET_KEY: z.string(),
  S3_MAX_UPLOAD_BYTES: z.coerce.number().int().positive(),
  S3_UPLOAD_EXPIRES_SECONDS: z.coerce.number().int().positive(),
})

const parsed = schema.safeParse({
  GRAPHQL_URL: process.env.GRAPHQL_URL,
  FILES_URL: process.env.FILES_URL ?? process.env.NEXT_PUBLIC_FILES_URL,
  S3_PUBLIC_ENDPOINT: process.env.S3_PUBLIC_ENDPOINT,
  S3_BUCKET: process.env.S3_BUCKET,
  S3_REGION: process.env.S3_REGION,
  S3_ACCESS_KEY: process.env.S3_ACCESS_KEY,
  S3_SECRET_KEY: process.env.S3_SECRET_KEY,
  S3_MAX_UPLOAD_BYTES: process.env.S3_MAX_UPLOAD_BYTES,
  S3_UPLOAD_EXPIRES_SECONDS: process.env.S3_UPLOAD_EXPIRES_SECONDS,
})

if (!parsed.success) {
  throw new Error(`Invalid server environment: ${z.prettifyError(parsed.error)}`)
}

export const serverEnv = parsed.data
