import { PackageCheck, PackageSearch } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { PageHeader } from "@/components/layout/page-header"

export default async function ItemsHeader({ kind, city }: { kind: ItemKind; city?: string }) {
  const [t, tc] = await Promise.all([getTranslations("list"), getTranslations("city")])
  const lost = kind === "lost"
  const where = city ? tc(`${city}.in`) : undefined

  return (
    <PageHeader
      title={
        where
          ? t(lost ? "lostTitleIn" : "foundTitleIn", { in: where })
          : t(lost ? "lostTitle" : "foundTitle")
      }
      description={lost ? t("lostDescription") : t("foundDescription")}
      icon={lost ? <PackageSearch className="size-9" /> : <PackageCheck className="size-9" />}
      tone={lost ? "pink" : "mint"}
    />
  )
}
