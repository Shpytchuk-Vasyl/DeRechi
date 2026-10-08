import { Wrench } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { paths } from "@/i18n/paths"
import { clientEnv } from "@/lib/env/client"

type Props = { params: Promise<{ locale: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "maintenance" })
  return { title: t("title"), description: t("metaDescription"), robots: { index: false } }
}

export default async function MaintenanceRoute() {
  if (!clientEnv.NEXT_PUBLIC_MAINTENANCE) notFound()

  const t = await getTranslations("maintenance")

  return (
    <div className="flex flex-col items-center gap-6 py-12 text-center">
      <Blob icon={<Wrench />} tone="yellow" size="lg" />
      <Heading level={1}>{t("title")}</Heading>
      <Text size="lg" muted className="block max-w-md">
        {t("text")}
      </Text>
      <LinkButton href={paths.home} size="lg">
        {t("home")}
      </LinkButton>
    </div>
  )
}
