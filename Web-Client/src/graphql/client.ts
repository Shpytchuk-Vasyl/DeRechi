import "server-only"
import { serverEnv } from "@/lib/env/server"
import type { TypedDocumentString } from "./generated/graphql"

type GraphQLError = {
  message: string
  path?: (string | number)[]
  extensions?: { classification?: string; retryAfter?: string }
}

export class GraphQLRequestError extends Error {
  constructor(
    message: string,
    readonly errors: GraphQLError[],
  ) {
    super(message)
    this.name = "GraphQLRequestError"
  }

  get isNotFound(): boolean {
    return this.errors.some((error) => error.extensions?.classification === "NOT_FOUND")
  }

  get isDisposableEmail(): boolean {
    return this.errors.some((error) => error.extensions?.classification === "DISPOSABLE_EMAIL")
  }

  get isPaymentUnavailable(): boolean {
    return this.errors.some((error) => error.extensions?.classification === "PAYMENT_UNAVAILABLE")
  }

  get unlockLimit(): { retryAfter: string | null } | null {
    const error = this.errors.find((it) => it.extensions?.classification === "UNLOCK_LIMIT")
    return error ? { retryAfter: error.extensions?.retryAfter ?? null } : null
  }
}

type RequestOptions = { revalidate?: number; tags?: string[] } | { cache: "no-store" }

export async function graphqlRequest<TResult, TVariables>(
  document: TypedDocumentString<TResult, TVariables>,
  variables: TVariables,
  options: RequestOptions = {},
): Promise<TResult> {
  const response = await fetch(serverEnv.GRAPHQL_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/graphql-response+json, application/json",
    },
    body: JSON.stringify({ query: document.toString(), variables }),
    ...("cache" in options
      ? { cache: options.cache }
      : { next: { revalidate: options.revalidate, tags: options.tags } }),
  })

  if (!response.ok) {
    throw new GraphQLRequestError(`GraphQL request failed with ${response.status}`, [])
  }

  const body = (await response.json()) as { data?: TResult; errors?: GraphQLError[] }

  console.log("GraphQL Request:", document.toString().slice(0, 100), variables, body)

  if (body.errors?.length) {
    throw new GraphQLRequestError(body.errors.map((error) => error.message).join("; "), body.errors)
  }

  if (!body.data) {
    throw new GraphQLRequestError("GraphQL response carried no data", [])
  }

  return body.data
}
