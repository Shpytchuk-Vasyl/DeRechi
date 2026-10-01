import ItemDetailPage from "@/screens/found_lost/item-detail"

type Props = { params: Promise<{ locale: string; id: string }> }

export default async function LostItemModalRoute({ params }: Props) {
  const { id } = await params
  return <ItemDetailPage kind="lost" id={id} compact />
}
