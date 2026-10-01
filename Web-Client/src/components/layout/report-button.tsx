"use client"

import { useTranslations } from "next-intl"
import { LinkButton } from "@/components/pouf/link-button"
import { usePathname } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export function ReportButton() {
  const t = useTranslations("nav")
  const pathname = usePathname()

  if (pathname === paths.list("found")) {
    return <LinkButton href={paths.report("found")}>{t("reportFound")}</LinkButton>
  }
  if (pathname === paths.list("lost")) {
    return <LinkButton href={paths.report("lost")}>{t("reportLost")}</LinkButton>
  }
  return null
}
