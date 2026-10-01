import { cx } from "class-variance-authority"
import { CalendarDays, Check, MapPin, Sparkles } from "lucide-react"
import { getFormatter, getTranslations } from "next-intl/server"
import type { ItemKind } from "@/api/items"
import { CategoryArt } from "@/components/items/category-art"
import { MetaRow } from "@/components/items/meta-row"
import { Card } from "@/components/pouf/card"
import { Stack } from "@/components/pouf/layout"
import { Badge } from "@/components/pouf/media"
import { Heading } from "@/components/pouf/text"
import { formatHryvnia } from "@/lib/money"

type Sample = {
  id: "keys" | "student" | "wallet" | "backpack" | "earbuds" | "cat"
  category: string
  hoursAgo: number
}

const SAMPLES: Record<ItemKind, [Sample, Sample, Sample]> = {
  found: [
    { id: "student", category: "DOCUMENTS", hoursAgo: 26 },
    { id: "wallet", category: "WALLET", hoursAgo: 5 },
    { id: "keys", category: "KEYS", hoursAgo: 2 },
  ],
  lost: [
    { id: "earbuds", category: "ELECTRONICS", hoursAgo: 30 },
    { id: "cat", category: "ANIMALS", hoursAgo: 8 },
    { id: "backpack", category: "BAGS", hoursAgo: 1 },
  ],
}

const REWARD: Record<ItemKind, number> = { found: 500, lost: 1000 }

const MOVE = "transition-[rotate,translate] ease-out motion-reduce:transition-none"
const SLOTS = [
  "left-[2%] top-10 w-[50%] -rotate-6 group-hover:-translate-x-3 group-hover:-rotate-9",
  "right-[2%] top-4 w-[50%] rotate-5 group-hover:translate-x-3 group-hover:rotate-8",
  "left-1/2 top-16 z-10 w-[54%] -translate-x-1/2 -rotate-1 group-hover:-translate-y-2",
]

export default async function NoticeStack({ kind }: { kind: ItemKind }) {
  const t = await getTranslations()
  const format = await getFormatter()
  const now = Date.now()

  const reward = t("item.reward", {
    amount: formatHryvnia(format, REWARD[kind]),
  })

  return (
    <div className="relative mx-auto h-80 w-full max-w-md" aria-hidden>
      {SAMPLES[kind].map((sample, index) => (
        <div key={sample.id} className={cx("absolute", MOVE, SLOTS[index])}>
          <MiniNotice
            title={t(`home.sample.${sample.id}.title`)}
            place={t(`home.sample.${sample.id}.place`)}
            categoryKey={sample.category}
            when={format.relativeTime(now - sample.hoursAgo * 3_600_000, now)}
          />
        </div>
      ))}

      <span
        className={cx(
          "absolute top-10 left-[14%] z-20 -rotate-6 rounded-pill shadow-ink/20 shadow-lg",
          MOVE,
          "group-hover:-translate-y-1",
        )}
      >
        <Badge tone={kind === "found" ? "up" : "down"}>
          {kind === "found" ? <Check className="size-3.5" /> : <Sparkles className="size-3.5" />}
          {kind === "found" ? t("home.sample.returned") : t("home.sample.new")}
        </Badge>
      </span>
      <span
        className={cx(
          "absolute right-[10%] bottom-6 z-20 rotate-3 rounded-pill shadow-ink/20 shadow-lg",
          MOVE,
          "group-hover:translate-y-1",
        )}
      >
        <Badge tone="yellow">{reward}</Badge>
      </span>
    </div>
  )
}

function MiniNotice({
  title,
  place,
  categoryKey,
  when,
}: {
  title: string
  place: string
  categoryKey: string
  when: string
}) {
  return (
    <Card variant="flush" className="overflow-hidden">
      <CategoryArt categoryKey={categoryKey} className="h-24" iconClassName="size-10" />
      <div className="px-3.5 pt-2.5 pb-4">
        <Stack gap={1}>
          <Heading level={3} className="text-[15px] leading-tight">
            {title}
          </Heading>
          <MetaRow icon={<MapPin />} truncate>
            {place}
          </MetaRow>
          <MetaRow icon={<CalendarDays />} faint>
            {when}
          </MetaRow>
        </Stack>
      </div>
    </Card>
  )
}
