"use client"

import { useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"
import { SortSelect } from "@/components/form/sort-select"
import type { ItemSort } from "@/graphql/generated/graphql"
import { usePathname, useRouter } from "@/i18n/navigation"
import { SORTS } from "@/lib/item-search"

const DEFAULT_SORT: ItemSort = "DATE_DESC"

export default function ItemSortSelect({ value }: { value: ItemSort }) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const t = useTranslations("sort")
  const tl = useTranslations("list")

  function change(next: ItemSort) {
    const query = new URLSearchParams(params.toString())
    if (next === DEFAULT_SORT) {
      query.delete("sort")
    } else {
      query.set("sort", next)
    }

    const search = query.toString()
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false })
  }

  return (
    <SortSelect
      className="hidden md:flex"
      label={tl("sort")}
      options={SORTS.map((sort) => ({ value: sort, label: t(sort) }))}
      defaultOption={DEFAULT_SORT}
      value={value}
      onChange={change}
    />
  )
}
