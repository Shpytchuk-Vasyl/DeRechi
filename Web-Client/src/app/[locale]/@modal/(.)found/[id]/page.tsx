import ItemDetailPage from "@/screens/found_lost/item-detail"

type Props = { params: Promise<{ locale: string; id: string }> }

export default async function FoundItemModalRoute({ params }: Props) {
  const { id } = await params
  return <ItemDetailPage kind="found" id={id} compact />
}
