"use client"

import { useTranslations } from "next-intl"

export function useFieldMessage(known: ReadonlySet<string>, namespace = "form") {
  const t = useTranslations(namespace)

  return (key?: string): string | undefined => {
    if (!key) return undefined
    return t(`error.${known.has(key) ? key : "invalid"}`)
  }
}
