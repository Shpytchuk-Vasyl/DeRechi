"use client"

import { ArrowUpDown, Check } from "lucide-react"
import { Button } from "@/components/pouf/Button"
import { DropdownMenu } from "@/components/pouf/menu"

export type SortOption<T extends string> = { value: T; label: string }

type Props<T extends string> = {
  options: SortOption<T>[]
  defaultOption: T
  value?: T
  onChange: (value: T) => void
  label: string
  className?: string
}

export function SortSelect<T extends string>({
  options,
  defaultOption,
  value,
  onChange,
  label,
  className,
}: Props<T>) {
  const current = value ?? defaultOption
  const chosen = options.find((option) => option.value === current) ?? options[0]

  return (
    <DropdownMenu
      label={label}
      items={options.map((option) => ({
        label: option.label,
        icon: option.value === current ? <Check className="size-4" aria-hidden /> : undefined,
        onClick: () => onChange(option.value),
      }))}
    >
      <Button className={className} variant="quiet" size="sm">
        <ArrowUpDown className="size-4" aria-hidden />
        {chosen?.label}
      </Button>
    </DropdownMenu>
  )
}
