import { mkdirSync, writeFileSync } from "node:fs"
import path from "node:path"
import type { FullConfig } from "@playwright/test"
import { categories } from "./support/api"
import { newRunId, STATE_FILE } from "./support/data"
import { env } from "./support/env"
import { seedShared } from "./support/shared"

export default async function globalSetup(_config: FullConfig): Promise<void> {
  const site = await fetch(new URL("/uk", env.baseURL)).catch((error: unknown) => {
    throw new Error(`e2e: the site at ${env.baseURL} does not answer (${String(error)})`)
  })
  if (site.status >= 500) throw new Error(`e2e: ${env.baseURL}/uk answered ${site.status}`)

  const found = await categories().catch((error: unknown) => {
    throw new Error(`e2e: GraphQL at ${env.graphqlURL} does not answer (${String(error)})`)
  })
  if (found.length === 0) throw new Error("e2e: the API has no categories")

  const runId = newRunId()
  process.env.E2E_RUN_ID = runId
  mkdirSync(path.dirname(STATE_FILE), { recursive: true })
  writeFileSync(
    STATE_FILE,
    `${JSON.stringify({ runId, startedAt: new Date().toISOString() }, null, 2)}\n`,
  )

  const shared = await seedShared()
  console.log(
    `e2e run ${runId}, shared set ${shared.marker} (anchor ${shared.anchor}, ${shared.created} created)`,
  )
}
