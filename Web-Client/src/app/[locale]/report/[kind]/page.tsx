import { FilePen } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { getTranslations } from "next-intl/server"
import { fetchCategories } from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
import { PageHeader } from "@/components/layout/page-header"
import { paths } from "@/i18n/paths"
import { routing } from "@/i18n/routing"
import { ITEM_KINDS, isItemKind } from "@/lib/kinds"
import { pageAlternates } from "@/lib/seo"
import { MAX_UPLOAD_BYTES } from "@/lib/uploads/storage"
import ReportForm from "@/screens/report/report-form"

type Props = { params: Promise<{ locale: string; kind: string }> }

export function generateStaticParams() {
  return routing.locales.flatMap((locale) => ITEM_KINDS.map((kind) => ({ locale, kind })))
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, kind } = await params
  if (!isItemKind(kind)) return {}

  const t = await getTranslations({ locale, namespace: "form" })
  const meta = KIND_META[kind].form

  return {
    title: t(meta.pageTitle),
    description: t(meta.metaDescription),
    alternates: pageAlternates(locale, paths.report(kind)),
  }
}

export default async function ReportRoute({ params }: Props) {
  const { kind } = await params
  if (!isItemKind(kind)) {
    notFound()
  }

  const t = await getTranslations("form")
  const categories = await fetchCategories()

  return (
    <>
      <PageHeader
        title={t(KIND_META[kind].form.pageTitle)}
        description={t("pageDescription")}
        icon={<FilePen className="size-9" />}
      />

      <ReportForm kind={kind} categories={categories} maxUploadBytes={MAX_UPLOAD_BYTES} />
    </>
  )
}
