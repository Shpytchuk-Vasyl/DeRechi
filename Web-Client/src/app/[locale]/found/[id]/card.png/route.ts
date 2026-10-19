import { itemCardImage } from "@/screens/found_lost/item-og-image"

type Context = { params: Promise<{ locale: string; id: string }> }

export async function GET(_request: Request, { params }: Context) {
  const { locale, id } = await params
  return itemCardImage("found", locale, id)
}
