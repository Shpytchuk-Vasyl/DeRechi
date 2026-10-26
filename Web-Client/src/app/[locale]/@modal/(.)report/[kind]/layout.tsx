import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import type { ReactNode } from "react"
import { KIND_META } from "@/components/items/kind-meta"
import { RouteDialog } from "@/components/layout/route-dialog"
import { isItemKind } from "@/lib/kinds"

type Props = { children: ReactNode; params: Promise<{ locale: string; kind: string }> }

export default async function ReportModalLayout({ children, params }: Props) {
  const { kind } = await params
  if (!isItemKind(kind)) {
    notFound()
  }

  const t = await getTranslations("form")

  return (
    <RouteDialog title={t(KIND_META[kind].form.pageTitle)} description={t("pageDescription")}>
      {children}
    </RouteDialog>
  )
}
