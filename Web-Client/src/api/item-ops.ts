import "server-only"
import type { ItemDetail, ItemSummary } from "@/api/items"
import { graphqlRequest } from "@/graphql/client"
import {
  ClaimFoundItemMutation,
  ClaimLostItemMutation,
  CreateFoundItemMutation,
  CreateLostItemMutation,
  FoundItemQuery,
  FoundItemsQuery,
  LostItemQuery,
  LostItemsQuery,
} from "@/graphql/documents"
import type {
  ContactInfoInput,
  ItemFilterInput,
  ItemInput,
  ItemSort,
} from "@/graphql/generated/graphql"
import { type CachePolicy, NO_STORE } from "@/lib/cache"
import type { ItemKind } from "@/lib/kinds"

type ListVariables = {
  filter: ItemFilterInput | null
  sort: ItemSort
  first: number
  after: string | null
}

type Connection = {
  edges: { node: ItemSummary }[]
  pageInfo: { hasNextPage: boolean; endCursor: string | null }
}

type ItemOps = {
  list: (variables: ListVariables, cache: CachePolicy) => Promise<Connection>
  one: (id: string, cache: CachePolicy) => Promise<ItemDetail | null>
  create: (input: ItemInput) => Promise<{ id: string }>
  claim: (id: string, contact: ContactInfoInput) => Promise<{ id: string; repeated: boolean }>
}

export const ITEM_OPS: Record<ItemKind, ItemOps> = {
  lost: {
    list: async (variables, cache) =>
      (await graphqlRequest(LostItemsQuery, variables, cache)).lostItems,
    one: async (id, cache) => (await graphqlRequest(LostItemQuery, { id }, cache)).lostItem,
    create: async (input) =>
      (await graphqlRequest(CreateLostItemMutation, { input }, NO_STORE)).createLostItem,
    claim: async (id, contact) =>
      (await graphqlRequest(ClaimLostItemMutation, { id, contact }, NO_STORE)).claimLostItem,
  },
  found: {
    list: async (variables, cache) =>
      (await graphqlRequest(FoundItemsQuery, variables, cache)).foundItems,
    one: async (id, cache) => (await graphqlRequest(FoundItemQuery, { id }, cache)).foundItem,
    create: async (input) =>
      (await graphqlRequest(CreateFoundItemMutation, { input }, NO_STORE)).createFoundItem,
    claim: async (id, contact) =>
      (await graphqlRequest(ClaimFoundItemMutation, { id, contact }, NO_STORE)).claimFoundItem,
  },
}
