import { notFound } from "next/navigation"
import type { ReactNode } from "react"
import { cityBySlug } from "@/lib/cities"
import ItemsHeader from "@/screens/found_lost/items-header"

type Props = { children: ReactNode; params: Promise<{ city: string }> }

export default async function CityFoundListLayout({ children, params }: Props) {
  const { city } = await params
  if (!cityBySlug(city)) {
    notFound()
  }

  return (
    <>
      <ItemsHeader kind="found" city={city} />
      {children}
    </>
  )
}
