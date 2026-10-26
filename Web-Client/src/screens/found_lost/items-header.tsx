import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
import { PageHeader } from "@/components/layout/page-header"

export default async function ItemsHeader({ kind, city }: { kind: ItemKind; city?: string }) {
  const [t, tc] = await Promise.all([getTranslations("list"), getTranslations("city")])
  const { list, Icon, tint } = KIND_META[kind]
  const where = city ? tc(`${city}.in`) : undefined

  return (
    <PageHeader
      title={where ? t(list.titleIn, { in: where }) : t(list.title)}
      description={t(list.description)}
      icon={<Icon className="size-9" />}
      tone={tint}
    />
  )
}
