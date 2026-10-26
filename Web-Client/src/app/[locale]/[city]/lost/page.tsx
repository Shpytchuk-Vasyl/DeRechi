import type { Metadata } from "next"
import type { RawSearchParams } from "@/lib/item-search"
import ItemsPage from "@/screens/found_lost/items-page"
import { listMetadata } from "@/screens/found_lost/list-page"

type Props = {
  params: Promise<{ locale: string; city: string }>
  searchParams: Promise<RawSearchParams>
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const [{ locale, city }, searchParamsValue] = await Promise.all([params, searchParams])
  return listMetadata("lost", locale, searchParamsValue, city)
}

export default async function CityLostItemsRoute({ params, searchParams }: Props) {
  const [{ city }, searchParamsValue] = await Promise.all([params, searchParams])

  return <ItemsPage kind="lost" city={city} searchParams={searchParamsValue} />
}
