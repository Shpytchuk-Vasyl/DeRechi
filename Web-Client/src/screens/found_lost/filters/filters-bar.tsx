"use client"

import { ListFilter, X } from "lucide-react"
import { useTranslations } from "next-intl"
import type { SubmitEvent } from "react"
import type { Category } from "@/api/items"
import { IconButton } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { Text } from "@/components/pouf/text"
import { CategoryField, DatesField, SearchField } from "./filter-fields"
import type { FilterDraft } from "./use-filter-draft"

type Props = {
  draft: FilterDraft
  patch: (changes: Partial<FilterDraft>) => void
  apply: (event: SubmitEvent) => void
  reset: () => void
  categories: Category[]
  canReset: boolean
  dirty: boolean
}

export default function FiltersBar({
  draft,
  patch,
  apply,
  reset,
  categories,
  canReset,
  dirty,
}: Props) {
  const t = useTranslations("list")

  return (
    <Card variant="tight" className="sticky top-2 z-30 hidden md:block">
      <form
        onSubmit={apply}
        className="grid gap-4 md:grid-cols-[1fr_200px_260px_auto] md:items-end"
      >
        <SearchField draft={draft} patch={patch} />
        <CategoryField draft={draft} patch={patch} categories={categories} />
        <DatesField draft={draft} patch={patch} />
        <div className="flex gap-2">
          <IconButton
            type="submit"
            variant="solid"
            label={t("apply")}
            icon={<ListFilter className="size-5" />}
          />
          {canReset ? (
            <IconButton label={t("reset")} icon={<X className="size-5" />} onClick={reset} />
          ) : null}
        </div>
        <Text size="sm" muted className={`md:col-span-4 ${!dirty && "hidden"}`}>
          {t("applyHint")}
        </Text>
      </form>
    </Card>
  )
}
