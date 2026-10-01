"use server"

import { fetchCategories, fetchItems, type ItemKind, type ItemPage } from "@/api/items"
import { type ItemSearch, toFilter } from "@/lib/item-search"

export async function loadMoreItems(
  kind: ItemKind,
  search: ItemSearch,
  after: string,
): Promise<ItemPage> {
  const categories = await fetchCategories()

  return fetchItems(kind, {
    filter: toFilter(search, categories),
    sort: search.sort,
    after,
  })
}
