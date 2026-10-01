import { type RefObject, useEffect } from "react"

export type TypewriterOptions = {
  typeMs?: number
  eraseMs?: number
  holdMs?: number
  gapMs?: number
  caret?: string
}

export function useTypewriter(
  ref: RefObject<HTMLInputElement | HTMLTextAreaElement | null>,
  phrases: readonly string[],
  fallback: string,
  enabled: boolean,
  { typeMs = 70, eraseMs = 35, holdMs = 1600, gapMs = 400, caret = "|" }: TypewriterOptions = {},
) {
  useEffect(() => {
    const input = ref.current
    if (!input) return
    if (!enabled || phrases.length === 0) {
      input.placeholder = fallback
      return
    }

    let phrase = 0
    let length = 0
    let erasing = false
    let timer: ReturnType<typeof setTimeout> | undefined

    const tick = () => {
      const current = phrases[phrase]
      length += erasing ? -1 : 1
      input.placeholder = `${current.slice(0, length)}${caret}`

      if (!erasing && length === current.length) {
        erasing = true
        timer = setTimeout(tick, holdMs)
      } else if (erasing && length === 0) {
        erasing = false
        phrase = (phrase + 1) % phrases.length
        timer = setTimeout(tick, gapMs)
      } else {
        timer = setTimeout(tick, erasing ? eraseMs : typeMs)
      }
    }

    const start = () => {
      timer = setTimeout(tick, gapMs)
    }
    const stop = () => {
      clearTimeout(timer)
      input.placeholder = fallback
    }

    input.addEventListener("focus", stop)
    input.addEventListener("blur", start)
    if (document.activeElement !== input) start()

    return () => {
      input.removeEventListener("focus", stop)
      input.removeEventListener("blur", start)
      stop()
    }
  }, [ref, phrases, fallback, enabled, typeMs, eraseMs, holdMs, gapMs, caret])
}
