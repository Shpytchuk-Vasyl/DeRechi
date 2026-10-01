import { PackageCheck, PackageSearch } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { PageHeader } from "@/components/layout/page-header"

export default async function ItemsHeader({ kind }: { kind: ItemKind }) {
  const t = await getTranslations("list")
  const lost = kind === "lost"

  return (
    <PageHeader
      title={lost ? t("lostTitle") : t("foundTitle")}
      description={lost ? t("lostDescription") : t("foundDescription")}
      icon={lost ? <PackageSearch className="size-9" /> : <PackageCheck className="size-9" />}
      tone={lost ? "pink" : "mint"}
    />
  )
}
