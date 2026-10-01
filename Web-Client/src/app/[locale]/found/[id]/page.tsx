import type { Metadata } from "next"
import ItemDetailPage, { itemMetadata } from "@/screens/found_lost/item-detail"

type Props = { params: Promise<{ locale: string; id: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, id } = await params
  return itemMetadata("found", locale, id)
}

export default async function FoundItemRoute({ params }: Props) {
  const { id } = await params

  return <ItemDetailPage kind="found" id={id} />
}
