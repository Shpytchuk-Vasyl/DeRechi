import { getTranslations } from "next-intl/server"
import { Card } from "@/components/pouf/card"
import { Stack } from "@/components/pouf/layout"
import { LinkButton } from "@/components/pouf/link-button"
import { Heading, Text } from "@/components/pouf/text"
import { SafetyLink, SafetyNote } from "@/components/safety/safety-note"
import { paths } from "@/i18n/paths"
import HeroScene from "@/screens/home/hero-scene"

export default async function HomeHero(props: React.ComponentProps<typeof Card>) {
  const t = await getTranslations("home")
  const tn = await getTranslations("nav")

  return (
    <Card variant="hero" {...props}>
      <div className="grid items-center gap-8 lg:grid-cols-[1.3fr_1fr]">
        <Stack gap={5}>
          <Stack gap={2}>
            <Heading level={1}>{t("heroTitle")}</Heading>
            <Text size="lg" muted>
              {t("heroText")}
            </Text>
          </Stack>

          <div className="grid gap-3 sm:grid-cols-2">
            <LinkButton href={paths.report("found")} tone="up" size="lg" block>
              {tn("reportFound")}
            </LinkButton>
            <LinkButton href={paths.report("lost")} tone="down" size="lg" block>
              {tn("reportLost")}
            </LinkButton>
          </div>

          <Stack gap={1}>
            <SafetyNote>
              {t.rich("safetyNote", { link: (chunks) => <SafetyLink>{chunks}</SafetyLink> })}
            </SafetyNote>
            <Text size="sm" muted>
              {t("rewardNote")}
            </Text>
          </Stack>
        </Stack>

        <div className="hidden lg:block">
          <HeroScene />
        </div>
      </div>
    </Card>
  )
}
