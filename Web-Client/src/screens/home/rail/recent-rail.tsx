import { getTranslations } from "next-intl/server"
import { ErrorBoundary } from "react-error-boundary"
import { fetchItems, type ItemKind } from "@/api/items"
import { ItemCard } from "@/components/items/item-card"
import { KIND_META } from "@/components/items/kind-meta"
import { ErrorNote } from "@/components/pouf/feedback"
import { Row, Spacer, Stack } from "@/components/pouf/layout"
import { Heading, Text } from "@/components/pouf/text"
import type { NearInput } from "@/graphql/generated/graphql"
import { CACHE, type CachePolicy } from "@/lib/cache"
import SeeAllLink from "./see-all-link"

const RAIL_SIZE = 5

type Props = {
  kind: ItemKind
  near?: NearInput
  categoryId?: string
  title?: string
  seeAll?: { label: string; pathname?: string; query?: Record<string, string> }
  empty?: string
  cache?: (kind: ItemKind) => CachePolicy
}

export default async function RecentRail({
  kind,
  near,
  categoryId,
  title,
  seeAll,
  empty,
  cache = CACHE.homeItems,
}: Props) {
  const t = await getTranslations("home")
  const messages = KIND_META[kind].home
  const headingId = `recent-${kind}-heading`

  return (
    <Stack asChild gap={3}>
      <section aria-labelledby={headingId}>
        <Row gap={3} align="center">
          <Heading level={2} id={headingId}>
            {title ?? t(messages.recent)}
          </Heading>
          <Spacer />
          <SeeAllLink kind={kind} pathname={seeAll?.pathname} query={seeAll?.query}>
            {seeAll?.label ?? t(messages.seeAll)}
          </SeeAllLink>
        </Row>
        <ErrorBoundary
          fallback={<ErrorNote className="self-center!">{t(messages.recentError)}</ErrorNote>}
        >
          <RailItems kind={kind} near={near} categoryId={categoryId} empty={empty} cache={cache} />
        </ErrorBoundary>
      </section>
    </Stack>
  )
}

async function RailItems({
  kind,
  near,
  categoryId,
  empty,
  cache,
}: Pick<Props, "kind" | "near" | "categoryId" | "empty"> & Required<Pick<Props, "cache">>) {
  const { items } = await fetchItems(
    kind,
    {
      first: RAIL_SIZE,
      ...(near || categoryId ? { filter: { near, categoryId } } : {}),
    },
    cache(kind),
  )

  if (items.length === 0) {
    return empty ? <Text muted>{empty}</Text> : null
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-5 max-lg:[&>*:nth-child(5)]:hidden">
      {items.map((item) => (
        <ItemCard key={item.id} item={item} kind={kind} />
      ))}
    </div>
  )
}
