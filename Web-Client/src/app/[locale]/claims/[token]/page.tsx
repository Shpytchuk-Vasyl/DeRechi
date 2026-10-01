import type { Metadata } from "next"
import { getTranslations } from "next-intl/server"
import ConfirmReturn from "@/screens/claims/confirm-return"

type Props = { params: Promise<{ locale: string; token: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "claim.confirm" })

  return {
    title: t("title"),
    description: t("text"),
    robots: { index: false, follow: false },
  }
}

export default async function ClaimRoute({ params }: Props) {
  const { token } = await params
  return <ConfirmReturn token={token} />
}
