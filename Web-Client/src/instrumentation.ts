import type { Instrumentation } from "next"

export const onRequestError: Instrumentation.onRequestError = (error, request, context) => {
  console.error(
    JSON.stringify({
      level: "error",
      event: "request_error",
      message: error instanceof Error ? error.message : String(error),
      digest: hasDigest(error) ? String(error.digest) : undefined,
      stack: error instanceof Error ? error.stack : undefined,
      method: request.method,
      path: request.path.split("?")[0],
      route: context.routePath,
      routeType: context.routeType,
      renderSource: "renderSource" in context ? context.renderSource : undefined,
    }),
  )
}

function hasDigest(error: unknown): error is { digest: unknown } {
  return typeof error === "object" && error !== null && "digest" in error
}
