import { PackageCheck, PackageSearch } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { Card } from "@/components/pouf/card"
import { Empty } from "@/components/pouf/feedback"
import { Row } from "@/components/pouf/layout"
import { LinkButton } from "@/components/pouf/link-button"
import { paths } from "@/i18n/paths"
import { hasActiveFilters, type ItemSearch } from "@/lib/item-search"

type Props = {
  kind: ItemKind
  search: ItemSearch
}

export default async function ItemsEmpty({ kind, search }: Props) {
  const t = await getTranslations("list")
  const tn = await getTranslations("nav")
  const filtered = hasActiveFilters(search) || Boolean(search.near)

  if (filtered) {
    return (
      <Card>
        <Empty icon="search" title={t("empty")}>
          {t("emptyHint")}
        </Empty>
        <Row justify="center">
          <LinkButton href={paths.list(kind)}>{t("emptyAction")}</LinkButton>
        </Row>
      </Card>
    )
  }

  const other: ItemKind = kind === "found" ? "lost" : "found"

  return (
    <Card>
      <Empty
        icon={
          kind === "found" ? (
            <PackageCheck className="size-5" />
          ) : (
            <PackageSearch className="size-5" />
          )
        }
        title={t(kind === "found" ? "emptyFound" : "emptyLost")}
      >
        {t(kind === "found" ? "emptyFoundHint" : "emptyLostHint")}
      </Empty>
      <Row justify="center">
        <LinkButton href={paths.report(other)} tone={other === "found" ? "up" : "down"}>
          {tn(other === "found" ? "reportFound" : "reportLost")}
        </LinkButton>
      </Row>
    </Card>
  )
}
