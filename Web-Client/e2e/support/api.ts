import { env } from "./env"

export type ItemKind = "lost" | "found"
export type SocialMedia = "TELEGRAM" | "VIBER" | "WHATSAPP" | "SIGNAL" | "MESSENGER"
export type ItemSort = "DATE_DESC" | "DATE_ASC" | "TITLE_DESC" | "TITLE_ASC"

export type Category = { id: string; key: string }

export type PlaceInput = {
  id: string
  name: string
  lat: number
  lon: number
  countryCode: string
}

export type ItemContactInput = {
  phone: string
  email?: string | null
  socialMedias?: SocialMedia[]
}
export type ContactInput = { phone: string; email: string; socialMedias?: SocialMedia[] }

export type ItemInput = {
  title: string
  description?: string | null
  date: string
  compensation?: { amount: number; currency?: string | null } | null
  image?: string | null
  categoryId: string
  place: PlaceInput
  contact: ItemContactInput
}

export type Item = {
  id: string
  title: string
  description: string | null
  date: string
  compensation: { amount: number; currency: string } | null
  image: string | null
  category: Category
  place: { id: string; name: string; lat: number | null; lon: number | null; countryCode: string }
  contact: { id: string; phone: string; email: string | null }
}

export type Claim = {
  id: string
  repeated: boolean
}

type GraphQLErrorBody = {
  message: string
  path?: (string | number)[]
  extensions?: { classification?: string; retryAfter?: string }
}

export class GraphQLError extends Error {
  readonly classification: string | undefined

  constructor(
    readonly operation: string,
    readonly errors: GraphQLErrorBody[],
  ) {
    const classification = errors[0]?.extensions?.classification
    super(
      `${operation}: ${classification ?? "ERROR"}: ${errors.map((it) => it.message).join("; ")}`,
    )
    this.name = "GraphQLError"
    this.classification = classification
  }
}

export async function graphql<T>(
  operation: string,
  query: string,
  variables: Record<string, unknown> = {},
): Promise<T> {
  const response = await fetch(env.graphqlURL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/graphql-response+json, application/json",
    },
    body: JSON.stringify({ query, variables }),
  })
  const text = await response.text()
  let body: { data?: T; errors?: GraphQLErrorBody[] }
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`${operation}: HTTP ${response.status}, not JSON`)
  }
  if (body.errors?.length) throw new GraphQLError(operation, body.errors)
  if (!response.ok || !body.data) throw new Error(`${operation}: HTTP ${response.status}, no data`)
  return body.data
}

const ITEM_FIELDS = `
  id
  title
  description
  date
  compensation { amount currency }
  image
  category { id key }
  place { id name lat lon countryCode }
`

const capitalised = (kind: ItemKind) => (kind === "lost" ? "Lost" : "Found")

export async function createItem(kind: ItemKind, input: ItemInput): Promise<{ id: string }> {
  const field = `create${capitalised(kind)}Item`
  const data = await graphql<Record<string, { id: string }>>(
    field,
    `mutation ($input: ItemInput!) { ${field}(input: $input) { id } }`,
    { input },
  )
  return data[field]
}

export type ItemFilter = {
  search?: string
  categoryId?: string
  dateFrom?: string
  dateTo?: string
  near?: { lat: number; lon: number; radiusKm?: number }
}

export async function items(
  kind: ItemKind,
  filter: ItemFilter,
  options: { sort?: ItemSort; first?: number; after?: string } = {},
): Promise<{ items: Omit<Item, "contact">[]; hasNextPage: boolean; endCursor: string | null }> {
  const field = `${kind}Items`
  const data = await graphql<
    Record<
      string,
      {
        edges: { node: Omit<Item, "contact"> }[]
        pageInfo: { hasNextPage: boolean; endCursor: string | null }
      }
    >
  >(
    field,
    `query ($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {
      ${field}(filter: $filter, sort: $sort, first: $first, after: $after) {
        edges { node { ${ITEM_FIELDS} } }
        pageInfo { hasNextPage endCursor }
      }
    }`,
    { filter, sort: options.sort ?? "DATE_DESC", first: options.first ?? 15, after: options.after },
  )
  const connection = data[field]
  return {
    items: connection.edges.map((edge) => edge.node),
    hasNextPage: connection.pageInfo.hasNextPage,
    endCursor: connection.pageInfo.endCursor,
  }
}

export async function claim(kind: ItemKind, id: string, contact: ContactInput): Promise<Claim> {
  const field = `claim${capitalised(kind)}Item`
  const data = await graphql<Record<string, Claim>>(
    field,
    `mutation ($id: ID!, $contact: ContactInfoInput!) {
      ${field}(id: $id, contact: $contact) { id repeated }
    }`,
    { id, contact },
  )
  return data[field]
}

let categoriesCache: Promise<Category[]> | null = null

export function categories(): Promise<Category[]> {
  categoriesCache ??= graphql<{ categories: Category[] }>(
    "categories",
    "query { categories { id key } }",
  ).then((data) => data.categories)
  categoriesCache.catch(() => {
    categoriesCache = null
  })
  return categoriesCache
}

export async function categoryId(key: string): Promise<string> {
  const found = (await categories()).find((category) => category.key === key)
  if (!found) throw new Error(`e2e: no category ${key}`)
  return found.id
}

export async function countries(): Promise<{ code: string; currency: string }[]> {
  const data = await graphql<{ countries: { code: string; currency: string }[] }>(
    "countries",
    "query { countries { code currency } }",
  )
  return data.countries
}
