"use client"

import { useTranslations } from "next-intl"
import type { ItemKind } from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
import { LinkButton } from "@/components/pouf/link-button"
import { usePathname } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { cityListKind } from "@/lib/cities"

function listKind(pathname: string): ItemKind | undefined {
  if (pathname === paths.list("found")) return "found"
  if (pathname === paths.list("lost")) return "lost"
  return cityListKind(pathname)
}

export function ReportButton() {
  const t = useTranslations("nav")
  const kind = listKind(usePathname())

  if (!kind) return null

  return (
    <LinkButton href={paths.report(kind)} prefetch={false}>
      {t(KIND_META[kind].nav.report)}
    </LinkButton>
  )
}
