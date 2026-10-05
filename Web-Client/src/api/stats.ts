import "server-only"
import { graphqlRequest } from "@/graphql/client"
import { StatsQuery } from "@/graphql/documents"
import { CACHE } from "@/lib/cache"

export type Stats = {
  returnedThisWeek: number
  foundToday: number
}

export async function fetchStats(): Promise<Stats | null> {
  try {
    const { stats } = await graphqlRequest(StatsQuery, {}, CACHE.stats())
    return stats
  } catch (error) {
    console.error("stats query failed, hiding the figures", error)
    return null
  }
}
