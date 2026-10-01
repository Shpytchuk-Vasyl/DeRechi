import { ArrowRight } from "lucide-react"
import type { ItemKind } from "@/api/items"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export default function SeeAllLink({ kind, children }: { kind: ItemKind; children: string }) {
  return (
    <Link
      href={paths.list(kind)}
      className="group inline-flex items-center font-extrabold no-underline"
    >
      {children}
      <span
        className="inline-flex w-0 -translate-x-2 overflow-hidden opacity-0 transition-[width,margin,opacity,translate] ease-out group-hover:ml-1.5 group-hover:w-4 group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:ml-1.5 group-focus-visible:w-4 group-focus-visible:translate-x-0 group-focus-visible:opacity-100 motion-reduce:transition-none"
        aria-hidden
      >
        <ArrowRight className="size-4 shrink-0 stroke-[2.5]" />
      </span>
    </Link>
  )
}
