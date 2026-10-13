import { itemOgImage } from "@/screens/found_lost/item-og-image"

export const alt = "DeRechi"
export const size = { width: 1200, height: 630 }
export const contentType = "image/png"

type Props = { params: Promise<{ locale: string; id: string }> }

export default async function Image({ params }: Props) {
  const { locale, id } = await params
  return itemOgImage("lost", locale, id)
}
