import { ItemFiltersSkeleton } from "@/screens/found_lost/filters/item-filters"
import { ItemListSkeleton } from "@/screens/found_lost/item-list"

export default function Loading() {
  return (
    <div aria-busy="true">
      <ItemFiltersSkeleton />
      <div className="mt-6 mb-4">
        <ItemListSkeleton />
      </div>
    </div>
  )
}
