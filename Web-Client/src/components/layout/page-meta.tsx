import type { ReactElement } from "react"

export type PageMetaItem = { icon: ReactElement; label: string }

export function PageMeta({ items }: { items: PageMetaItem[] }) {
  return (
    <div className="-mt-3 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-muted text-sm">
      {items.map((item) => (
        <span
          key={item.label}
          className="inline-flex items-center gap-1.5 [&>svg]:size-4 [&>svg]:shrink-0"
        >
          {item.icon}
          {item.label}
        </span>
      ))}
    </div>
  )
}
