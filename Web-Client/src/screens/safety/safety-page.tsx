import {
  Archive,
  CalendarClock,
  Camera,
  CreditCard,
  EyeOff,
  HandCoins,
  IdCard,
  KeyRound,
  Link2Off,
  Lock,
  MailCheck,
  Search,
  ShieldCheck,
  Siren,
  Users,
} from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ReactElement } from "react"
import type { ItemKind } from "@/api/items"
import { Card } from "@/components/pouf/card"
import { Stack } from "@/components/pouf/layout"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"
import { LEGAL_CONTACT } from "@/content/legal"

type Point = { key: string; icon: ReactElement; tone: Tone }

const PROTECT: Point[] = [
  { key: "contacts", icon: <EyeOff />, tone: "pink" },
  { key: "replies", icon: <MailCheck />, tone: "blue" },
  { key: "card", icon: <CreditCard />, tone: "mint" },
  { key: "limit", icon: <CalendarClock />, tone: "yellow" },
  { key: "accounts", icon: <KeyRound />, tone: "orange" },
  { key: "moderation", icon: <ShieldCheck />, tone: "purple" },
  { key: "retention", icon: <Archive />, tone: "blue" },
]

const TIPS: { title: string; tint?: ItemKind; tone: Tone; points: Omit<Point, "tone">[] }[] = [
  {
    title: "tipsAllTitle",
    tone: "purple",
    points: [
      { key: "payOnSite", icon: <Link2Off /> },
      { key: "codes", icon: <Lock /> },
      { key: "meet", icon: <Users /> },
    ],
  },
  {
    title: "tipsLostTitle",
    tint: "lost",
    tone: "down",
    points: [
      { key: "noPrepay", icon: <HandCoins /> },
      { key: "proof", icon: <Camera /> },
    ],
  },
  {
    title: "tipsFoundTitle",
    tint: "found",
    tone: "up",
    points: [
      { key: "verify", icon: <Search /> },
      { key: "documents", icon: <IdCard /> },
    ],
  },
]

export default async function SafetyPage() {
  const t = await getTranslations("safety")

  return (
    <div className="flex flex-col gap-6">
      <Card variant="hero">
        <Heading level={1} className="mb-2">
          {t("title")}
        </Heading>
        <Text size="lg" muted>
          {t("intro")}
        </Text>
      </Card>

      <section aria-labelledby="safety-protect" className="flex flex-col gap-4">
        <Heading level={2} id="safety-protect">
          {t("protectTitle")}
        </Heading>
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PROTECT.map((point) => (
            <li key={point.key}>
              <Card className="h-full">
                <Blob icon={point.icon} tone={point.tone} size="sm" />
                <Heading level={3} className="whitespace-normal text-lg">
                  {t(`protect.${point.key}.title`)}
                </Heading>
                <Text muted className="leading-relaxed">
                  {t(`protect.${point.key}.text`)}
                </Text>
              </Card>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="safety-tips" className="flex flex-col gap-4">
        <Heading level={2} id="safety-tips">
          {t("tipsTitle")}
        </Heading>
        {TIPS.map((group) => (
          <Card key={group.title} tint={group.tint}>
            <Heading level={3} className="whitespace-normal text-lg">
              {t(group.title)}
            </Heading>
            <ul className="flex flex-col gap-4">
              {group.points.map((point) => (
                <li key={point.key} className="flex items-start gap-3.5">
                  <Blob icon={point.icon} tone={group.tone} size="sm" />
                  <div>
                    <Text className="block font-bold">{t(`tips.${point.key}.title`)}</Text>
                    <Text muted className="mt-0.5 block leading-relaxed">
                      {t(`tips.${point.key}.text`)}
                    </Text>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </section>

      <Card className="flex items-start gap-3.5">
        <Blob icon={<Siren />} tone="warn" size="sm" />
        <div>
          <Heading level={2} className="whitespace-normal text-lg">
            {t("troubleTitle")}
          </Heading>
          <Text muted className="mt-1 block leading-relaxed">
            {t("troubleText", { email: LEGAL_CONTACT.email })}
          </Text>
        </div>
      </Card>
    </div>
  )
}
