import { z } from "zod"

const schema = z.object({
  NEXT_PUBLIC_SITE_URL: z.url(),
  NEXT_PUBLIC_MAPS_API_KEY: z.string().optional(),
  NEXT_PUBLIC_FILES_URL: z.url(),
  NEXT_PUBLIC_SMS_OUTAGE: z.stringbool().default(true),
})

const parsed = schema.safeParse({
  NEXT_PUBLIC_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL,
  NEXT_PUBLIC_MAPS_API_KEY: process.env.NEXT_PUBLIC_MAPS_API_KEY,
  NEXT_PUBLIC_FILES_URL: process.env.NEXT_PUBLIC_FILES_URL,
  NEXT_PUBLIC_SMS_OUTAGE: process.env.NEXT_PUBLIC_SMS_OUTAGE,
})

if (!parsed.success) {
  throw new Error(`Invalid public environment: ${z.prettifyError(parsed.error)}`)
}

export const clientEnv = parsed.data

export function fileUrl(key: string): string {
  if (key.startsWith("items"))
    return `${clientEnv.NEXT_PUBLIC_FILES_URL}/${key.replace(/^\/+/, "")}`
  return `/files/${key.replace(/^\/+/, "")}`
}

export function absoluteFileUrl(key: string): string {
  return new URL(fileUrl(key), clientEnv.NEXT_PUBLIC_SITE_URL).toString()
}
