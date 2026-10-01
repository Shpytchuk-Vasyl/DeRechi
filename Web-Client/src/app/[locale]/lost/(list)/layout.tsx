import type { ReactNode } from "react"
import ItemsHeader from "@/screens/found_lost/items-header"

export default function LostListLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <ItemsHeader kind="lost" />
      {children}
    </>
  )
}
