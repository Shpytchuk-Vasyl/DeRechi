"use client"

import { MessageCircleMore } from "lucide-react"
import { useLocale, useTranslations } from "next-intl"
import { type MouseEvent, useState } from "react"
import { buttonClasses } from "@/components/pouf/Button"
import { toast } from "@/components/pouf/toaster"
import { usePathname } from "@/i18n/navigation"
import { clientEnv } from "@/lib/env/client"
import { feedbackForm, feedbackPage, feedbackUrl } from "@/lib/feedback"

const TALLY_EMBED = "https://tally.so/widgets/embed.js"
const CLOSE_AFTER_SUBMIT_MS = 3000
const POPUP_MAX_WIDTH = 600

type TallyPopupOptions = {
  layout?: "default" | "modal"
  width?: number
  autoClose?: number
  hiddenFields?: Record<string, string>
}

type Tally = { openPopup(formId: string, options?: TallyPopupOptions): void }

declare global {
  interface Window {
    Tally?: Tally
  }
}

let tallyScript: Promise<Tally | undefined> | undefined

function loadTally(): Promise<Tally | undefined> {
  tallyScript ??= new Promise((resolve) => {
    const script = document.createElement("script")
    const fail = () => {
      tallyScript = undefined
      script.remove()
      resolve(undefined)
    }
    script.src = TALLY_EMBED
    script.async = true
    script.onload = () => (window.Tally ? resolve(window.Tally) : fail())
    script.onerror = fail
    document.head.append(script)
  })
  return tallyScript
}

function popupWidth(): number {
  return Math.min(window.innerWidth, POPUP_MAX_WIDTH)
}

async function showPopup(form: string, page: string): Promise<boolean> {
  const tally = await loadTally()
  if (!tally) return false
  try {
    tally.openPopup(form, {
      layout: "modal",
      width: popupWidth(),
      autoClose: CLOSE_AFTER_SUBMIT_MS,
      hiddenFields: { page, originPage: page },
    })
    return true
  } catch {
    return false
  }
}

function opensElsewhere(event: MouseEvent): boolean {
  return event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
}

export function FeedbackButton() {
  const t = useTranslations("nav")
  const locale = useLocale()
  const pathname = usePathname()
  const [opening, setOpening] = useState(false)
  const form = feedbackForm(clientEnv.NEXT_PUBLIC_TALLY_FORM, locale)
  if (!form) return null

  const page = feedbackPage(pathname)

  const open = (event: MouseEvent<HTMLAnchorElement>) => {
    if (opensElsewhere(event)) return
    event.preventDefault()
    if (opening) return
    setOpening(true)
    void showPopup(form, page).then((shown) => {
      setOpening(false)
      if (!shown) toast.warning(t("feedbackUnavailable"))
    })
  }

  return (
    <a
      href={feedbackUrl(form, { page })}
      target="_blank"
      rel="noopener"
      aria-label={t("feedback")}
      title={t("feedback")}
      aria-busy={opening}
      onClick={open}
      className={buttonClasses({ variant: "quiet", size: "sm", shape: "icon" })}
    >
      <MessageCircleMore className="size-4" aria-hidden />
    </a>
  )
}
