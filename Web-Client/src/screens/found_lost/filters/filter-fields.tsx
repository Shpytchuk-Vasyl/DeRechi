"use client"

import { useTranslations } from "next-intl"
import type { Category } from "@/api/items"
import { DateRangePicker } from "@/components/form/date-picker"
import { Select } from "@/components/pouf/controls"
import { Field, Input } from "@/components/pouf/Input"
import type { ItemSort } from "@/graphql/generated/graphql"
import { todayIso } from "@/lib/dates"
import { SORTS } from "@/lib/item-search"
import { ANY_CATEGORY, type FilterDraft } from "./use-filter-draft"

type FieldProps = {
  draft: FilterDraft
  patch: (changes: Partial<FilterDraft>) => void
}

export function SearchField({ draft, patch }: FieldProps) {
  const t = useTranslations("list")

  return (
    <Field label={t("search")}>
      {(id, describedBy) => (
        <Input
          id={id}
          describedBy={describedBy}
          type="search"
          enterKeyHint="search"
          value={draft.search}
          placeholder={t("searchHint")}
          onChange={(search) => patch({ search })}
        />
      )}
    </Field>
  )
}

export function CategoryField({
  draft,
  patch,
  categories,
}: FieldProps & { categories: Category[] }) {
  const t = useTranslations("list")
  const tc = useTranslations("category")

  return (
    <Field label={t("category")}>
      {(id, describedBy) => (
        <Select
          id={id}
          describedBy={describedBy}
          value={draft.category}
          onChange={(category) => patch({ category })}
          options={[
            { value: ANY_CATEGORY, label: t("allCategories") },
            ...categories.map((category) => ({ value: category.key, label: tc(category.key) })),
          ]}
        />
      )}
    </Field>
  )
}

export function DatesField({ draft, patch, inline = false }: FieldProps & { inline?: boolean }) {
  const t = useTranslations("list")

  return (
    <Field label={t("dateRange")}>
      {(id) => (
        <DateRangePicker
          id={id}
          from={draft.dateFrom}
          to={draft.dateTo}
          placeholder={t("anyDate")}
          ariaLabel={t("dateRange")}
          inline={inline}
          max={todayIso()}
          onChange={(range) => patch({ dateFrom: range.from, dateTo: range.to })}
        />
      )}
    </Field>
  )
}

export function SortField({ draft, patch }: FieldProps) {
  const t = useTranslations("list")
  const ts = useTranslations("sort")

  return (
    <Field label={t("sort")}>
      {(id, describedBy) => (
        <Select
          id={id}
          describedBy={describedBy}
          value={draft.sort}
          onChange={(sort) => patch({ sort: sort as ItemSort })}
          options={SORTS.map((sort) => ({ value: sort, label: ts(sort) }))}
        />
      )}
    </Field>
  )
}
