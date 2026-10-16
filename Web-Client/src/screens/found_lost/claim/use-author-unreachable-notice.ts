import { useTranslations } from "next-intl"
import { useCallback, useRef } from "react"
import { dismissToast, type ToastAction, toast } from "@/components/pouf/toaster"
import type { UnreachableAuthor } from "@/lib/unreachable-author"

export function useAuthorUnreachableNotice() {
  const t = useTranslations("claim.authorUnreachable")
  const tf = useTranslations("form")
  const shown = useRef<string | null>(null)

  return useCallback(
    ({ socialMedia, nickName, profileUrl }: UnreachableAuthor) => {
      const copyNickname = async () => {
        if (await copyText(nickName)) toast.success(t("copied"))
        else toast.error(t("copyFailed"))
      }
      const actions: ToastAction[] = [
        ...(profileUrl ? [{ label: t("openProfile"), href: profileUrl }] : []),
        { label: t("copy"), onClick: copyNickname },
      ]

      if (shown.current) dismissToast(shown.current)
      shown.current = toast.warning(t("text", { network: tf(`social.${socialMedia}`), nickName }), {
        duration: Number.POSITIVE_INFINITY,
        actions,
      })
    },
    [t, tf],
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
