import { Archive, CalendarClock, EyeOff, KeyRound, MailCheck, ShieldCheck } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ReactElement } from "react"
import { PageHeader } from "@/components/layout/page-header"
import { Card } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"
import { LEGAL_CONTACT } from "@/content/legal"

type Point = { key: string; icon: ReactElement; tone: Tone }

const PROTECT: Point[] = [
  { key: "contacts", icon: <EyeOff />, tone: "pink" },
  { key: "replies", icon: <MailCheck />, tone: "blue" },
  { key: "limit", icon: <CalendarClock />, tone: "yellow" },
  { key: "accounts", icon: <KeyRound />, tone: "orange" },
  { key: "moderation", icon: <ShieldCheck />, tone: "purple" },
  { key: "retention", icon: <Archive />, tone: "mint" },
]

const RULES = ["prepay", "codes", "verify", "meet", "documents", "report"]

export default async function SafetyPage() {
  const t = await getTranslations("safety")

  return (
    <>
      <PageHeader
        title={t("title")}
        description={t("intro")}
        icon={<ShieldCheck className="size-9" />}
        tone="mint"
      />

      <div className="flex flex-col gap-6">
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

        <section aria-labelledby="safety-rules" className="flex flex-col gap-4">
          <Heading level={2} id="safety-rules">
            {t("rulesTitle")}
          </Heading>
          <Card>
            <ol className="flex flex-col gap-5">
              {RULES.map((key, index) => (
                <li key={key} className="flex items-start gap-3.5">
                  <Blob icon={<span className="font-black">{index + 1}</span>} size="sm" />
                  <div>
                    <Text className="block font-bold">{t(`rules.${key}.title`)}</Text>
                    <Text muted className="mt-0.5 block leading-relaxed">
                      {t(`rules.${key}.text`, { email: LEGAL_CONTACT.email })}
                    </Text>
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </section>
      </div>
    </>
  )
}
