import type { Metadata } from "next"
import type { RawSearchParams } from "@/lib/item-search"
import ItemsPage from "@/screens/found_lost/items-page"
import { listMetadata } from "@/screens/found_lost/list-page"

type Props = {
  params: Promise<{ locale: string }>
  searchParams: Promise<RawSearchParams>
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const [{ locale }, searchParamsValue] = await Promise.all([params, searchParams])
  return listMetadata("found", locale, searchParamsValue)
}

export default async function FoundItemsRoute({ searchParams }: Props) {
  const searchParamsValue = await searchParams

  return <ItemsPage kind="found" searchParams={searchParamsValue} />
}
