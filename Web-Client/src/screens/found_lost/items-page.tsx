import { getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { Text } from "@/components/pouf/text"
import type { RawSearchParams } from "@/lib/item-search"
import ItemFilters from "@/screens/found_lost/filters/item-filters"
import ItemList from "@/screens/found_lost/item-list"
import ItemSortSelect from "@/screens/found_lost/item-sort"
import ItemsEmpty from "@/screens/found_lost/items-empty"
import { loadListPage } from "@/screens/found_lost/list-page"
import NearbySwitch from "@/screens/found_lost/nearby-switch"

type Props = {
  kind: ItemKind
  searchParams: RawSearchParams
}

export default async function ItemsPage({ kind, searchParams }: Props) {
  const t = await getTranslations("list")
  const { search, categories, page, after } = await loadListPage(kind, searchParams)

  return (
    <>
      <ItemFilters key={JSON.stringify(search)} search={search} categories={categories} />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <Text size="sm" muted className="flex-1">
          {page.items.length > 0 ? t("showingSome", { shown: page.items.length }) : null}
        </Text>
        {kind === "found" ? <NearbySwitch active={Boolean(search.near)} /> : null}

        <ItemSortSelect value={search.sort} />
      </div>

      <div className="mt-4 text-center">
        {page.items.length === 0 ? (
          <ItemsEmpty kind={kind} search={search} />
        ) : (
          <ItemList
            key={JSON.stringify({ search, after })}
            kind={kind}
            search={search}
            initial={page}
          />
        )}
      </div>
    </>
  )
}
