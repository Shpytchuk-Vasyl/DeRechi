import { getTranslations } from "next-intl/server"
import type { ReactNode } from "react"
import { RouteDialog } from "@/components/layout/route-dialog"

export default async function FoundItemModalLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations("item")

  return (
    <RouteDialog title={t("found")} headerHidden>
      {children}
    </RouteDialog>
  )
}
