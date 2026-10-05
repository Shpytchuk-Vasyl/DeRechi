import { EyeOff, Gift, MessageSquare, Search, Timer } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ReactElement } from "react"
import type { ItemKind } from "@/api/items"
import { Card } from "@/components/pouf/card"
import { Stack } from "@/components/pouf/layout"
import { LinkButton } from "@/components/pouf/link-button"
import { Heading, Text } from "@/components/pouf/text"
import { paths } from "@/i18n/paths"
import NoticeStack from "../notice-stack"
import ExplainerPoint from "./explainer-point"

const SIDES: Record<
  ItemKind,
  { title: string; text: string; points: { key: string; icon: ReactElement }[] }
> = {
  found: {
    title: "foundTitle",
    text: "foundText",
    points: [
      { key: "foundPoint1", icon: <Timer /> },
      { key: "foundPoint2", icon: <EyeOff /> },
      { key: "foundPoint3", icon: <MessageSquare /> },
    ],
  },
  lost: {
    title: "lostTitle",
    text: "lostText",
    points: [
      { key: "lostPoint1", icon: <Search /> },
      { key: "lostPoint2", icon: <Gift /> },
      { key: "lostPoint3", icon: <EyeOff /> },
    ],
  },
}

export default async function HomeExplainer({ kind }: { kind: ItemKind }) {
  const t = await getTranslations("home")
  const tn = await getTranslations("nav")
  const side = SIDES[kind]

  return (
    <Card tint={kind} className="group grid items-center gap-8 lg:grid-cols-2" data-mascot-spot>
      <div className={kind === "lost" ? "hidden lg:order-2 lg:block" : "hidden lg:block"}>
        <NoticeStack kind={kind} />
      </div>
      <Stack gap={4}>
        <Heading level={2}>{t(side.title)}</Heading>
        <Text muted>{t(side.text)}</Text>
        <Stack gap={3}>
          {side.points.map((point) => (
            <ExplainerPoint key={point.key} kind={kind} icon={point.icon}>
              {t(point.key)}
            </ExplainerPoint>
          ))}
        </Stack>
        <div className="text-center">
          <LinkButton href={paths.report(kind)} tone={kind === "found" ? "up" : "down"} size="lg">
            {tn(kind === "found" ? "reportFound" : "reportLost")}
          </LinkButton>
        </div>
      </Stack>
    </Card>
  )
}
