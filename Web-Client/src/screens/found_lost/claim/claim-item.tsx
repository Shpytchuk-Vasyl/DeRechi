import { createContext, useContext } from "react"
import type { ItemKind } from "@/api/items"

export type ClaimItem = {
  kind: ItemKind
  itemId: string
  countryCode: string
}

const Context = createContext<ClaimItem | null>(null)

export const ClaimItemProvider = Context.Provider

export function useClaimItem(): ClaimItem {
  const item = useContext(Context)
  if (item === null) throw new Error("useClaimItem must be used inside ClaimCard")
  return item
}
