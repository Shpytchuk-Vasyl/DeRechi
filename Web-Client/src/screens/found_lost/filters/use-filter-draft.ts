"use client"

import { type SubmitEvent, useState } from "react"
import type { ItemSort } from "@/graphql/generated/graphql"
import { usePathname, useRouter } from "@/i18n/navigation"
import { type ItemSearch, toQuery } from "@/lib/item-search"

export const ANY_CATEGORY = "ANY"

export const DEFAULT_SORT: ItemSort = "DATE_DESC"

export type FilterDraft = {
  search: string
  category: string
  dateFrom?: string
  dateTo?: string
  sort: ItemSort
}

const EMPTY: FilterDraft = {
  search: "",
  category: ANY_CATEGORY,
  dateFrom: undefined,
  dateTo: undefined,
  sort: DEFAULT_SORT,
}

function fromSearch(search: ItemSearch): FilterDraft {
  return {
    search: search.search ?? "",
    category: search.category ?? ANY_CATEGORY,
    dateFrom: search.dateFrom,
    dateTo: search.dateTo,
    sort: search.sort,
  }
}

export function isDirty(draft: FilterDraft, search: ItemSearch): boolean {
  const applied = fromSearch(search)
  return (
    draft.search.trim() !== applied.search ||
    draft.category !== applied.category ||
    draft.dateFrom !== applied.dateFrom ||
    draft.dateTo !== applied.dateTo ||
    draft.sort !== applied.sort
  )
}

export function useFilterDraft(search: ItemSearch) {
  const router = useRouter()
  const pathname = usePathname()
  const [draft, setDraft] = useState(() => fromSearch(search))

  function patch(changes: Partial<FilterDraft>) {
    setDraft((current) => ({ ...current, ...changes }))
  }

  function apply(event: SubmitEvent) {
    event.preventDefault()

    const next = toQuery({
      search: draft.search.trim() || undefined,
      category: draft.category === ANY_CATEGORY ? undefined : draft.category,
      dateFrom: draft.dateFrom,
      dateTo: draft.dateTo,
      sort: draft.sort,
      near: search.near,
    })
    const query = new URLSearchParams(next).toString()
    router.push(query ? `${pathname}?${query}` : pathname)
  }

  function reset() {
    setDraft(EMPTY)
    router.push(pathname)
  }

  return { draft, patch, apply, reset }
}
