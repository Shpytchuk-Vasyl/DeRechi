import "server-only"
import { graphqlRequest } from "@/graphql/client"
import { StatsQuery } from "@/graphql/documents"

export type Stats = {
  returnedThisWeek: number
  foundToday: number
}

const STATS_REVALIDATE = 300

const MOCK_STATS: Stats | null = { returnedThisWeek: 37, foundToday: 12 }

export async function fetchStats(): Promise<Stats | null> {
  if (MOCK_STATS) {
    return MOCK_STATS
  }

  try {
    const { stats } = await graphqlRequest(
      StatsQuery,
      {},
      { revalidate: STATS_REVALIDATE, tags: ["stats"] },
    )
    return stats
  } catch {
    return null
  }
}
