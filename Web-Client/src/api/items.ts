import "server-only"
import { ITEM_OPS } from "@/api/item-ops"
import { graphqlRequest } from "@/graphql/client"
import { CategoriesQuery } from "@/graphql/documents"
import { requestFailure } from "@/graphql/failure"
import type { ItemFilterInput, ItemSort } from "@/graphql/generated/graphql"
import { CACHE, type CachePolicy } from "@/lib/cache"
import type { Money } from "@/lib/intl/country"
import type { ItemKind } from "@/lib/kinds"

export type { ItemKind } from "@/lib/kinds"

export type Category = {
  id: string
  key: string
}

export type ItemSummary = {
  id: string
  title: string
  description?: string | null
  date: string
  compensation?: Money | null
  image?: string | null
  category: Category
  place: {
    id: string
    name: string
    lat?: number | null
    lon?: number | null
    countryCode: string
  }
}

export type ItemDetail = ItemSummary & {
  contact: { id: string; phone: string | null; email: string | null }
}

export type ItemPage = {
  items: ItemSummary[]
  endCursor?: string | null
  hasNextPage: boolean
}

export const PAGE_SIZE = 15

export type ItemQuery = {
  filter?: ItemFilterInput
  sort?: ItemSort
  first?: number
  after?: string | null
}

export async function fetchItems(
  kind: ItemKind,
  query: ItemQuery = {},
  cache: CachePolicy = CACHE.items(kind),
): Promise<ItemPage> {
  const connection = await ITEM_OPS[kind].list(
    {
      filter: query.filter ?? null,
      sort: query.sort ?? "DATE_DESC",
      first: query.first ?? PAGE_SIZE,
      after: query.after ?? null,
    },
    cache,
  )

  return {
    items: connection.edges.map((edge) => edge.node),
    endCursor: connection.pageInfo.endCursor,
    hasNextPage: connection.pageInfo.hasNextPage,
  }
}

export async function fetchItem(kind: ItemKind, id: string): Promise<ItemDetail | null> {
  try {
    return await ITEM_OPS[kind].one(id, CACHE.item(kind, id))
  } catch (error) {
    if (requestFailure(error) === "notFound") {
      return null
    }
    throw error
  }
}

export async function fetchCategories(): Promise<Category[]> {
  const { categories } = await graphqlRequest(CategoriesQuery, {}, CACHE.categories())
  return categories
}
