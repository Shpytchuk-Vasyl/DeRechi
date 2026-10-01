"use client"

import type { Category } from "@/api/items"
import { Card } from "@/components/pouf/card"
import { Skeleton } from "@/components/pouf/skeleton"
import { hasActiveFilters, type ItemSearch } from "@/lib/item-search"
import FiltersBar from "./filters-bar"
import FiltersSheet from "./filters-sheet"
import { DEFAULT_SORT, isDirty, useFilterDraft } from "./use-filter-draft"

type Props = {
  search: ItemSearch
  categories: Category[]
}

export default function ItemFilters({ search, categories }: Props) {
  const { draft, patch, apply, reset } = useFilterDraft(search)

  const activeCount =
    Number(Boolean(search.search)) +
    Number(Boolean(search.category)) +
    Number(Boolean(search.dateFrom || search.dateTo)) +
    Number(search.sort !== DEFAULT_SORT)

  return (
    <>
      <FiltersSheet
        draft={draft}
        patch={patch}
        apply={apply}
        reset={reset}
        categories={categories}
        activeCount={activeCount}
        canReset={hasActiveFilters(search) || Boolean(draft.search)}
      />
      <FiltersBar
        draft={draft}
        patch={patch}
        apply={apply}
        reset={reset}
        categories={categories}
        canReset={hasActiveFilters(search)}
        dirty={isDirty(draft, search)}
      />
    </>
  )
}

export function ItemFiltersSkeleton() {
  return (
    <>
      <Card variant="tight" className="hidden md:block">
        <Skeleton className="h-13" />
      </Card>
      <Skeleton className="h-13 md:hidden" />
    </>
  )
}
