"use client"

import { Check, Megaphone, QrCode, Share2, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { useCallback, useEffect, useRef, useState } from "react"
import type { ItemKind } from "@/api/items"
import { Button, IconButton } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { toast } from "@/components/pouf/toaster"
import { forgetJustPublished, isJustPublished } from "@/lib/browser-storage"
import { type SharePayload, shareOrCopy, shareOrDownloadImage } from "@/lib/share"

const COPIED_FOR_MS = 2_000
const REVOKE_AFTER_MS = 60_000

type Props = SharePayload & {
  kind: ItemKind
  id: string
  image: string
}

export default function ShareActions({ kind, id, image, ...payload }: Props) {
  const t = useTranslations("item")
  const tc = useTranslations("common")
  const [published, setPublished] = useState(false)

  useEffect(() => {
    setPublished(isJustPublished(kind, id))
  }, [kind, id])

  const buttons = (
    <ShareButtons
      payload={payload}
      image={image}
      fileName={`derechi-${kind}-${id}.png`}
      prefetch={published}
    />
  )

  if (!published) {
    return <div className="flex flex-wrap gap-2 sm:ml-auto">{buttons}</div>
  }

  const dismiss = () => {
    forgetJustPublished()
    setPublished(false)
  }

  return (
    <Card tint={kind} size="sm" className="mt-2 basis-full">
      <div className="flex items-start gap-3.5">
        <Blob icon={<Megaphone />} tone={kind === "lost" ? "pink" : "mint"} size="sm" />
        <div className="min-w-0 flex-1">
          <Heading level={2} className="whitespace-normal text-base">
            {t("publishedTitle")}
          </Heading>
          <Text muted className="mt-1 block leading-relaxed">
            {t(`publishedText.${kind}`)}
          </Text>
          <div className="mt-3.5 flex flex-wrap gap-2">{buttons}</div>
        </div>
        <IconButton label={tc("close")} icon={<X className="size-4" />} onClick={dismiss} />
      </div>
    </Card>
  )
}

type ShareButtonsProps = {
  payload: SharePayload
  image: string
  fileName: string
  prefetch: boolean
}

function ShareButtons({ payload, image, fileName, prefetch }: ShareButtonsProps) {
  const t = useTranslations("item")
  const [copiedAt, setCopiedAt] = useState<number | null>(null)
  const [preparing, setPreparing] = useState(false)
  const file = useRef<Promise<File | null> | null>(null)
  const copied = copiedAt !== null
  const LinkIcon = copied ? Check : Share2

  useEffect(() => {
    if (copiedAt === null) return
    const timer = setTimeout(() => setCopiedAt(null), COPIED_FOR_MS)
    return () => clearTimeout(timer)
  }, [copiedAt])

  // The share sheet only opens while the click still counts as the person's own gesture, and
  // rendering the card can outlast that, so the image is fetched ahead: as soon as the notice
  // was just published, otherwise when the pointer or focus reaches the button.
  const loadFile = useCallback(() => {
    file.current ??= fetchFile(image, fileName).then((ready) => {
      if (ready === null) file.current = null
      return ready
    })
    return file.current
  }, [image, fileName])

  useEffect(() => {
    if (prefetch) void loadFile()
  }, [prefetch, loadFile])

  // A second share() while the sheet is still open rejects with InvalidStateError, which would
  // otherwise fall through to copying and report "copied" over the open sheet.
  const sharing = useRef(false)

  const shareLink = async () => {
    if (sharing.current) return
    sharing.current = true
    try {
      const outcome = await shareOrCopy(payload, navigator)
      if (outcome === "copied") setCopiedAt(Date.now())
      else if (outcome === "failed") toast.error(t("shareFailed"))
    } finally {
      sharing.current = false
    }
  }

  const shareImage = async () => {
    setPreparing(true)
    const ready = await loadFile()
    if (ready === null) {
      setPreparing(false)
      toast.error(t("shareImageFailed"))
      return
    }

    const outcome = await shareOrDownloadImage(ready, payload, navigator, downloadFile)
    setPreparing(false)
    if (outcome === "failed") toast.error(t("shareImageFailed"))
  }

  return (
    <>
      <Button size="sm" variant="quiet" onClick={shareLink}>
        <LinkIcon className="size-4" aria-hidden />
        {t(copied ? "shareCopied" : "share")}
      </Button>
      <Button
        size="sm"
        variant="quiet"
        loading={preparing}
        onClick={shareImage}
        onPointerEnter={loadFile}
        onFocus={loadFile}
      >
        <QrCode className="size-4" aria-hidden />
        {t("shareImage")}
      </Button>
    </>
  )
}

async function fetchFile(url: string, name: string): Promise<File | null> {
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    const blob = await response.blob()
    return new File([blob], name, { type: blob.type || "image/png" })
  } catch {
    return null
  }
}

function downloadFile(file: File) {
  const url = URL.createObjectURL(file)
  const link = document.createElement("a")
  link.href = url
  link.download = file.name
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), REVOKE_AFTER_MS)
}
