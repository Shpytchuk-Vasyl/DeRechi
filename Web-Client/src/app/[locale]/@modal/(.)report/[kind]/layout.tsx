import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import type { ReactNode } from "react"
import type { ItemKind } from "@/api/items"
import { RouteDialog } from "@/components/layout/route-dialog"

type Props = { children: ReactNode; params: Promise<{ locale: string; kind: string }> }

const KINDS: ItemKind[] = ["lost", "found"]

export default async function ReportModalLayout({ children, params }: Props) {
  const { kind } = await params
  if (!KINDS.includes(kind as ItemKind)) {
    notFound()
  }

  const t = await getTranslations("form")

  return (
    <RouteDialog
      title={kind === "lost" ? t("pageTitleLost") : t("pageTitleFound")}
      description={t("pageDescription")}
    >
      {children}
    </RouteDialog>
  )
}
