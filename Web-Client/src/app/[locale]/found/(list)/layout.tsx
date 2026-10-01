import type { ReactNode } from "react"
import ItemsHeader from "@/screens/found_lost/items-header"

export default function FoundListLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <ItemsHeader kind="found" />
      {children}
    </>
  )
}
