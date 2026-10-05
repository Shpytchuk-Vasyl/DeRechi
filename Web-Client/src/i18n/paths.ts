import type { ItemKind } from "@/api/items"

export const paths = {
  home: "/",
  prize: "/prize",
  terms: "/terms",
  privacy: "/privacy",
  safety: "/safety",

  list: (kind: ItemKind) => `/${kind}` as const,
  item: (kind: ItemKind, id: string) => `/${kind}/${id}` as const,
  report: (kind: ItemKind) => `/report/${kind}` as const,
} as const
