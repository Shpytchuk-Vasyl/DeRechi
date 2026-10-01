import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import { Confetti } from "@/components/effects/confetti"
import { Znaida } from "@/components/mascot/znaida"
import { LinkButton } from "@/components/pouf/link-button"
import { Heading, Text } from "@/components/pouf/text"
import { paths } from "@/i18n/paths"

type Props = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "game" })
  return { title: t("prizeTitle"), robots: { index: false } }
}

export default async function PrizeRoute() {
  const t = await getTranslations("game")

  return (
    <div className="flex flex-col items-center gap-6 text-center">
      <Confetti />
      <div className="znaida-alive mx-auto w-56 sm:w-72">
        <Znaida look="center" className="h-auto w-full" />
      </div>
      <Heading level={1}>{t("prizeTitle")}</Heading>
      <Text size="lg" muted className="block max-w-md">
        {t("prizeText")}
      </Text>
      <LinkButton href={paths.home} size="lg">
        {t("prizeHome")}
      </LinkButton>
    </div>
  )
}
