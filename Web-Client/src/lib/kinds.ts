export const ITEM_KINDS = ["lost", "found"] as const

export type ItemKind = (typeof ITEM_KINDS)[number]

export function isItemKind(value: unknown): value is ItemKind {
  return ITEM_KINDS.includes(value as ItemKind)
}
