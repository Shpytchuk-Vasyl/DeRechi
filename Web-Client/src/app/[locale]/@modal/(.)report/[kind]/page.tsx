import { fetchCategories, type ItemKind } from "@/api/items"
import { MAX_UPLOAD_BYTES } from "@/lib/uploads/storage"
import ReportForm from "@/screens/report/report-form"

type Props = { params: Promise<{ locale: string; kind: string }> }

// The layout has already rejected an unknown kind, so the cast only narrows the type.
export default async function ReportModalRoute({ params }: Props) {
  const { kind } = await params
  const categories = await fetchCategories()

  return (
    <ReportForm
      kind={kind as ItemKind}
      categories={categories}
      maxUploadBytes={MAX_UPLOAD_BYTES}
      compact
    />
  )
}
