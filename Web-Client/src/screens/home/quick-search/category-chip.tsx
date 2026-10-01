import { categoryArt } from "@/components/items/category-art"
import { Badge } from "@/components/pouf/media"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export default function CategoryChip({ category, label }: { category: string; label: string }) {
  const { icon: Icon, tone } = categoryArt(category)

  return (
    <Link
      href={{ pathname: paths.list("found"), query: { category } }}
      className="group inline-flex no-underline"
    >
      <Badge tone={tone} className="gap-0 group-hover:gap-1.5 group-focus-visible:gap-1.5">
        <span
          className="inline-flex w-0 -translate-x-2 overflow-hidden opacity-0 transition-[width,opacity,translate] ease-out group-hover:w-4 group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:w-4 group-focus-visible:translate-x-0 group-focus-visible:opacity-100 motion-reduce:transition-none"
          aria-hidden
        >
          <Icon className="size-4 shrink-0 stroke-[2.25]" />
        </span>
        {label}
      </Badge>
    </Link>
  )
}
