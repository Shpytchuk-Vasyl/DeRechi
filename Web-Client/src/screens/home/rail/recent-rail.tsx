import { getTranslations } from "next-intl/server"
import { ErrorBoundary } from "react-error-boundary"
import { fetchItems, type ItemKind } from "@/api/items"
import { ItemCard } from "@/components/items/item-card"
import { ErrorNote } from "@/components/pouf/feedback"
import { Row, Spacer, Stack } from "@/components/pouf/layout"
import { Heading } from "@/components/pouf/text"
import SeeAllLink from "./see-all-link"

const RAIL_SIZE = 5

type Props = {
  kind: ItemKind
}

export default async function RecentRail({ kind }: Props) {
  const t = await getTranslations("home")

  return (
    <Stack gap={3}>
      <Row gap={3} align="center">
        <Heading level={2}>{t(kind === "found" ? "recentFound" : "recentLost")}</Heading>
        <Spacer />
        <SeeAllLink kind={kind}>{t(kind === "found" ? "seeAllFound" : "seeAllLost")}</SeeAllLink>
      </Row>
      <ErrorBoundary
        fallback={
          <ErrorNote className="self-center!">
            {t(kind === "found" ? "recentFoundError" : "recentLostError")}
          </ErrorNote>
        }
      >
        <RailItems kind={kind} />
      </ErrorBoundary>
    </Stack>
  )
}

async function RailItems({ kind }: Props) {
  const { items } = await fetchItems(kind, { first: RAIL_SIZE })

  if (items.length === 0) {
    return null
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-5 max-lg:[&>*:nth-child(5)]:hidden">
      {items.map((item) => (
        <ItemCard key={item.id} item={item} kind={kind} />
      ))}
    </div>
  )
}
