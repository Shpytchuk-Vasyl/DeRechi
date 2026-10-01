"use client"

import { useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import { type ReactNode, useId, useMemo, useRef, useState } from "react"
import { Input } from "@/components/pouf/Input"
import { useTypewriter } from "@/hooks/use-typewriter"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export default function HeroSearch() {
  const t = useTranslations("home")
  const router = useRouter()
  const reduceMotion = useReducedMotion()
  const [query, setQuery] = useState("")
  const inputRef = useRef<HTMLInputElement>(null)
  const hintId = useId()

  const placeholder = t("searchWhatPlaceholder")
  const examples = useMemo(() => t.raw("searchExamples") as string[], [t])
  useTypewriter(inputRef, examples, placeholder, !reduceMotion && query === "")

  function submit(event: React.SubmitEvent) {
    event.preventDefault()
    const search = query.trim()
    router.push(search ? { pathname: paths.list("found"), query: { search } } : paths.list("lost"))
  }

  const kbd = (chunks: ReactNode) => (
    <kbd className="rounded-control bg-bg px-1.5 py-0.5 font-black font-pouf text-sm">{chunks}</kbd>
  )

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <Input
        ref={inputRef}
        value={query}
        onChange={setQuery}
        type="search"
        name="search"
        enterKeyHint="search"
        describedBy={hintId}
        label={t("searchWhat")}
        placeholder={placeholder}
      />

      <p id={hintId} className="px-1 font-bold text-[13px] text-muted-foreground">
        <span className="pointer-coarse:hidden">{t.rich("searchHintDesktop", { kbd })}</span>
        <span className="pointer-coarse:inline hidden">{t.rich("searchHintTouch", { kbd })}</span>
      </p>
    </form>
  )
}
