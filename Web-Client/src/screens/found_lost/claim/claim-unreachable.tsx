import { Check, Copy, ExternalLink, MessageCircle } from "lucide-react"
import { useTranslations } from "next-intl"
import { useEffect, useState } from "react"
import { Button } from "@/components/pouf/Button"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { toast } from "@/components/pouf/toaster"
import type { UnreachableAuthor } from "@/lib/unreachable-author"

const COPIED_FOR_MS = 2_000

type Props = {
  author: UnreachableAuthor
}

export default function ClaimUnreachable({ author: { socialMedia, nickName, profileUrl } }: Props) {
  const t = useTranslations("claim.authorUnreachable")
  const tf = useTranslations("form")
  const [copiedAt, setCopiedAt] = useState<number | null>(null)
  const copied = copiedAt !== null
  const CopyIcon = copied ? Check : Copy

  useEffect(() => {
    if (copiedAt === null) return
    const timer = setTimeout(() => setCopiedAt(null), COPIED_FOR_MS)
    return () => clearTimeout(timer)
  }, [copiedAt])

  const copyNickname = async () => {
    if (await copyText(nickName)) setCopiedAt(Date.now())
    else toast.error(t("copyFailed"))
  }

  return (
    <div className="flex items-start gap-3.5">
      <Blob icon={<MessageCircle />} tone="blue" size="sm" />
      <div className="min-w-0 flex-1">
        <Heading level={3} className="whitespace-normal text-base">
          {t("title")}
        </Heading>
        <Text muted className="mt-1 block leading-relaxed">
          {t("text", { network: tf(`social.${socialMedia}`) })}
        </Text>

        <div className="cushion-field mt-4 flex flex-wrap items-center gap-2 rounded-control bg-bg py-2 pr-2 pl-4">
          <span className="wrap-anywhere min-w-0 flex-1 select-all font-black text-[17px]">
            {nickName}
          </span>
          <Button size="sm" variant="quiet" onClick={copyNickname}>
            <CopyIcon className="size-4" aria-hidden />
            {t(copied ? "copied" : "copy")}
          </Button>
        </div>

        {profileUrl ? (
          <div className="mt-4">
            <LinkButton href={profileUrl} target="_blank">
              {t("write")}
              <ExternalLink className="size-4" aria-hidden />
            </LinkButton>
          </div>
        ) : null}
      </div>
    </div>
  )
}

async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
