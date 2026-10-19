import type { ItemKind } from "@/api/items"

export const paths = {
  home: "/",
  prize: "/prize",
  terms: "/terms",
  privacy: "/privacy",
  safety: "/safety",
  maintenance: "/maintenance",

  list: (kind: ItemKind) => `/${kind}` as const,
  item: (kind: ItemKind, id: string) => `/${kind}/${id}` as const,
  card: (kind: ItemKind, id: string) => `/${kind}/${id}/card.png` as const,
  report: (kind: ItemKind) => `/report/${kind}` as const,
  city: (slug: string) => `/${slug}` as const,
} as const
