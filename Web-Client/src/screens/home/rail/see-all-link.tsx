import { ArrowRight } from "lucide-react"
import type { ItemKind } from "@/api/items"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

type Props = {
  kind: ItemKind
  query?: Record<string, string>
  children: string
}

export default function SeeAllLink({ kind, query, children }: Props) {
  return (
    <Link
      href={query ? { pathname: paths.list(kind), query } : paths.list(kind)}
      className="group inline-flex items-center font-extrabold no-underline ml-auto"
    >
      {children}
      <span
        className="inline-flex pointer-fine:w-0 pointer-fine:-translate-x-2 pointer-fine:overflow-hidden pointer-fine:opacity-0 transition-[width,margin,opacity,translate] ease-out group-hover:ml-1.5 group-hover:w-4 group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:ml-1.5 group-focus-visible:w-4 group-focus-visible:translate-x-0 group-focus-visible:opacity-100 motion-reduce:transition-none"
        aria-hidden
      >
        <ArrowRight className="size-4 shrink-0 stroke-[2.5]" />
      </span>
    </Link>
  )
}
