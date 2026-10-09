import { FilePen } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import { fetchCategories, type ItemKind } from "@/api/items"
import { PageHeader } from "@/components/layout/page-header"
import { paths } from "@/i18n/paths"
import { routing } from "@/i18n/routing"
import { pageAlternates } from "@/lib/seo"
import { MAX_UPLOAD_BYTES } from "@/lib/uploads/storage"
import ReportForm from "@/screens/report/report-form"

type Props = { params: Promise<{ locale: string; kind: string }> }

const KINDS: ItemKind[] = ["lost", "found"]

function toKind(value: string): ItemKind | null {
  return KINDS.includes(value as ItemKind) ? (value as ItemKind) : null
}

export function generateStaticParams() {
  return routing.locales.flatMap((locale) => KINDS.map((kind) => ({ locale, kind })))
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, kind: raw } = await params
  const kind = toKind(raw)
  if (!kind) return {}

  const t = await getTranslations({ locale, namespace: "form" })

  return {
    title: kind === "lost" ? t("pageTitleLost") : t("pageTitleFound"),
    description: t("pageDescription"),
    alternates: pageAlternates(locale, paths.report(kind)),
  }
}

export default async function ReportRoute({ params }: Props) {
  const { kind: raw } = await params
  const kind = toKind(raw)
  if (!kind) {
    notFound()
  }

  const t = await getTranslations("form")
  const categories = await fetchCategories()

  return (
    <>
      <PageHeader
        title={kind === "lost" ? t("pageTitleLost") : t("pageTitleFound")}
        description={t("pageDescription")}
        icon={<FilePen className="size-9" />}
      />

      <ReportForm kind={kind} categories={categories} maxUploadBytes={MAX_UPLOAD_BYTES} />
    </>
  )
}
