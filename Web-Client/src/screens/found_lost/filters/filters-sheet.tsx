"use client"

import * as Dialog from "@radix-ui/react-dialog"
import { ListFilter } from "lucide-react"
import { useTranslations } from "next-intl"
import { type SubmitEvent, useState } from "react"
import type { Category } from "@/api/items"
import { Button, IconButton } from "@/components/pouf/Button"
import { Heading } from "@/components/pouf/text"
import { CategoryField, DatesField, SearchField, SortField } from "./filter-fields"
import type { FilterDraft } from "./use-filter-draft"

type Props = {
  draft: FilterDraft
  patch: (changes: Partial<FilterDraft>) => void
  apply: (event: SubmitEvent) => void
  reset: () => void
  categories: Category[]
  activeCount: number
  canReset: boolean
}

export default function FiltersSheet({
  draft,
  patch,
  apply,
  reset,
  categories,
  activeCount,
  canReset,
}: Props) {
  const t = useTranslations("list")
  const [open, setOpen] = useState(false)

  function submit(event: SubmitEvent) {
    apply(event)
    setOpen(false)
  }

  function clear() {
    reset()
    setOpen(false)
  }

  return (
    <form onSubmit={apply} className="sticky top-2 z-30 flex items-end gap-2 md:hidden">
      <div className="min-w-0 flex-1 [&_label]:sr-only">
        <SearchField draft={draft} patch={patch} />
      </div>
      <div className="relative">
        <IconButton
          variant="solid"
          label={t("filters")}
          icon={<ListFilter className="size-5" />}
          onClick={() => setOpen(true)}
        />
        {activeCount > 0 ? (
          <span className="pointer-events-none absolute -top-1.5 -right-1.5 grid size-5 place-items-center rounded-pill bg-ink font-black text-white text-xs">
            {activeCount}
          </span>
        ) : null}
      </div>

      <Dialog.Root open={open} onOpenChange={setOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="pouf-overlay" />
          <Dialog.Content className="pouf-sheet" aria-describedby={undefined}>
            <div className="pouf-sheet__handle" />
            <Dialog.Title asChild>
              <Heading level={3}>{t("filters")}</Heading>
            </Dialog.Title>
            <form onSubmit={submit} className="pouf-sheet__body flex flex-col gap-4">
              <CategoryField draft={draft} patch={patch} categories={categories} />
              <DatesField draft={draft} patch={patch} inline />
              <SortField draft={draft} patch={patch} />
              <div className="mt-2 flex flex-col gap-2">
                <Button type="submit" size="lg" block>
                  {t("showResults")}
                </Button>
                {canReset ? (
                  <Button variant="quiet" block onClick={clear}>
                    {t("reset")}
                  </Button>
                ) : null}
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </form>
  )
}
