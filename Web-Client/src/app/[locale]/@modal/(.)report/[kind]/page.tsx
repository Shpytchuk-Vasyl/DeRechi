import { notFound } from "next/navigation"
import { fetchCategories } from "@/api/items"
import { isItemKind } from "@/lib/kinds"
import { MAX_UPLOAD_BYTES } from "@/lib/uploads/storage"
import ReportForm from "@/screens/report/report-form"

type Props = { params: Promise<{ locale: string; kind: string }> }

export default async function ReportModalRoute({ params }: Props) {
  const { kind } = await params
  if (!isItemKind(kind)) {
    notFound()
  }

  const categories = await fetchCategories()

  return (
    <ReportForm kind={kind} categories={categories} maxUploadBytes={MAX_UPLOAD_BYTES} compact />
  )
}
