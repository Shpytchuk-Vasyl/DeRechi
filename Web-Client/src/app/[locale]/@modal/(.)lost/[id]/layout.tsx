import { getTranslations } from "next-intl/server"
import type { ReactNode } from "react"
import { RouteDialog } from "@/components/layout/route-dialog"

export default async function LostItemModalLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations("item")

  return (
    <RouteDialog title={t("lost")} headerHidden>
      {children}
    </RouteDialog>
  )
}
