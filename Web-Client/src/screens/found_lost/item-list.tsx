"use client"

import { useTranslations } from "next-intl"
import { useState, useTransition } from "react"
import type { ItemKind, ItemPage, ItemSummary } from "@/api/items"
import { loadMoreItems } from "@/app/actions/items"
import { ItemCard, ItemCardSkeleton } from "@/components/items/item-card"
import { Button } from "@/components/pouf/Button"
import type { ItemSearch } from "@/lib/item-search"

type Props = {
  kind: ItemKind
  search: ItemSearch
  initial: ItemPage
}

const GRID = "grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-5"

const SKELETON_SLOTS = Array.from({ length: 10 }, (_, index) => `ItemListSkeleton-${index}`)

export function ItemListSkeleton() {
  return (
    <div className={GRID}>
      {SKELETON_SLOTS.map((slot) => (
        <ItemCardSkeleton key={slot} />
      ))}
    </div>
  )
}

export default function ItemList({ kind, search, initial }: Props) {
  const t = useTranslations("list")
  const [items, setItems] = useState<ItemSummary[]>(initial.items)
  const [cursor, setCursor] = useState(initial.endCursor)
  const [hasNextPage, setHasNextPage] = useState(initial.hasNextPage)
  const [isPending, startTransition] = useTransition()

  function showMore() {
    if (!cursor) return

    startTransition(async () => {
      const next = await loadMoreItems(kind, search, cursor)
      setItems((current) => [...current, ...next.items])
      setCursor(next.endCursor)
      setHasNextPage(next.hasNextPage)
    })
  }

  return (
    <>
      <div className={GRID}>
        {items.map((item) => (
          <ItemCard key={item.id} item={item} kind={kind} />
        ))}
      </div>

      {hasNextPage ? (
        <Button
          className="mt-7"
          type="button"
          variant="quiet"
          onClick={showMore}
          loading={isPending}
        >
          {t("showMore")}
        </Button>
      ) : null}
    </>
  )
}
