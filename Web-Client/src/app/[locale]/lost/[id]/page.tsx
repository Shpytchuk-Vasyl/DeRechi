import type { Metadata } from "next"
import ItemDetailPage, { itemMetadata } from "@/screens/found_lost/item-detail"

type Props = { params: Promise<{ locale: string; id: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, id } = await params
  return itemMetadata("lost", locale, id)
}

export default async function LostItemRoute({ params }: Props) {
  const { id } = await params
  return <ItemDetailPage kind="lost" id={id} />
}
