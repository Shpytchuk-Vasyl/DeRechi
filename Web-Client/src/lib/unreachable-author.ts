import type { SocialMedia } from "@/graphql/generated/graphql"

export type UnreachableAuthor = {
  socialMedia: SocialMedia
  nickName: string
  profileUrl: string | null
}

type ErrorWithExtensions = { extensions?: Record<string, unknown> }

const NETWORKS = {
  TELEGRAM: true,
  VIBER: true,
  WHATSAPP: true,
  FACEBOOK: true,
} satisfies Record<SocialMedia, true>

export function unreachableAuthor(
  errors: readonly ErrorWithExtensions[],
): UnreachableAuthor | null {
  const extensions = errors.find(
    (error) => error.extensions?.classification === "AUTHOR_UNREACHABLE",
  )?.extensions
  if (!extensions) return null

  const { socialMedia, nickName, profileUrl } = extensions
  if (!isNetwork(socialMedia) || typeof nickName !== "string" || nickName.trim() === "") {
    return null
  }
  return { socialMedia, nickName, profileUrl: httpsUrl(profileUrl) }
}

function isNetwork(value: unknown): value is SocialMedia {
  return typeof value === "string" && Object.hasOwn(NETWORKS, value)
}

function httpsUrl(value: unknown): string | null {
  if (typeof value !== "string" || !URL.canParse(value)) return null
  return new URL(value).protocol === "https:" ? value : null
}
