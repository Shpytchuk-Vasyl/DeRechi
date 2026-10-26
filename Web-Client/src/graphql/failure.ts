import { GraphQLRequestError } from "@/graphql/client"

export type RequestFailure = "notFound" | "disposableEmail" | "failed"

export function requestFailure(error: unknown): RequestFailure {
  if (!(error instanceof GraphQLRequestError)) return "failed"
  if (error.isNotFound) return "notFound"
  if (error.isDisposableEmail) return "disposableEmail"
  return "failed"
}
