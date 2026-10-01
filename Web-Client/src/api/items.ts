import "server-only"
import { GraphQLRequestError, graphqlRequest } from "@/graphql/client"
import {
  CategoriesQuery,
  FoundItemQuery,
  FoundItemsQuery,
  LostItemQuery,
  LostItemsQuery,
} from "@/graphql/documents"
import type { ItemFilterInput, ItemSort } from "@/graphql/generated/graphql"

export type ItemKind = "lost" | "found"

export type Category = {
  id: string
  key: string
}

export type ItemSummary = {
  id: string
  title: string
  description?: string | null
  date: string
  compensation?: number | null
  image?: string | null
  category: Category
  place: { id: string; name: string; lat?: number | null; lon?: number | null }
}

export type ItemDetail = ItemSummary & {
  contact: { id: string; phone: string; email: string }
}

export type ItemPage = {
  items: ItemSummary[]
  endCursor?: string | null
  hasNextPage: boolean
}

export const PAGE_SIZE = 15

const LIST_REVALIDATE = 60
const DETAIL_REVALIDATE = 3600
const CATEGORY_REVALIDATE = 3600

export type ItemQuery = {
  filter?: ItemFilterInput
  sort?: ItemSort
  first?: number
  after?: string | null
}

export async function fetchItems(kind: ItemKind, query: ItemQuery = {}): Promise<ItemPage> {
  const variables = {
    filter: query.filter ?? null,
    sort: query.sort ?? "DATE_DESC",
    first: query.first ?? PAGE_SIZE,
    after: query.after ?? null,
  }

  const options = { revalidate: LIST_REVALIDATE, tags: [`items:${kind}`] }

  const connection =
    kind === "lost"
      ? (await graphqlRequest(LostItemsQuery, variables, options)).lostItems
      : (await graphqlRequest(FoundItemsQuery, variables, options)).foundItems

  return {
    items: connection.edges.map((edge) => edge.node),
    endCursor: connection.pageInfo.endCursor,
    hasNextPage: connection.pageInfo.hasNextPage,
  }
}

export async function fetchItem(kind: ItemKind, id: string): Promise<ItemDetail | null> {
  const options = { revalidate: DETAIL_REVALIDATE, tags: [`item:${kind}:${id}`] }

  try {
    const item =
      kind === "lost"
        ? (await graphqlRequest(LostItemQuery, { id }, options)).lostItem
        : (await graphqlRequest(FoundItemQuery, { id }, options)).foundItem

    return item ?? null
  } catch (error) {
    if (error instanceof GraphQLRequestError && error.isNotFound) {
      return null
    }
    throw error
  }
}

export async function fetchCategories(): Promise<Category[]> {
  const { categories } = await graphqlRequest(
    CategoriesQuery,
    {},
    { revalidate: CATEGORY_REVALIDATE, tags: ["categories"] },
  )
  return categories
}
