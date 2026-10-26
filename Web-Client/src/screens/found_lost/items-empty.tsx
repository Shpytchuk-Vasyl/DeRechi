import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
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
          <LinkButton href={search.city ? paths.cityList(search.city, kind) : paths.list(kind)}>
            {t("emptyAction")}
          </LinkButton>
        </Row>
      </Card>
    )
  }

  const { list, Icon, other } = KIND_META[kind]

  return (
    <Card>
      <Empty icon={<Icon className="size-5" />} title={t(list.empty)}>
        {t(list.emptyHint)}
      </Empty>
      <Row justify="center">
        <LinkButton href={paths.report(other)} tone={KIND_META[other].tone} prefetch={false}>
          {tn(KIND_META[other].nav.report)}
        </LinkButton>
      </Row>
    </Card>
  )
}
