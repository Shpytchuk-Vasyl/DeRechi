"use client"

import { useTranslations } from "next-intl"
import { useFormContext, useWatch } from "react-hook-form"
import type { Category, ItemKind } from "@/api/items"
import { CategoryArt } from "@/components/items/category-art"
import { NoticeCard } from "@/components/items/item-card"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/pouf/card"
import { Heading } from "@/components/pouf/text"
import type { ReportDraft } from "@/schema/report-schema"

type Props = {
  kind: ItemKind
  categories: Category[]
  photoUrl: string | null
}

export default function PreviewAside({ kind, categories, photoUrl }: Props) {
  const t = useTranslations("form")
  const ti = useTranslations("item")
  const { control } = useFormContext<ReportDraft>()

  const [title, categoryId, place, date, compensation] = useWatch({
    control,
    name: ["title", "categoryId", "place", "date", "compensation"],
  })
  const categoryKey = categories.find((category) => category.id === categoryId)?.key

  return (
    <aside className="flex flex-col gap-6">
      <section className="flex flex-col gap-3">
        <Heading level={2}>{t("previewTitle")}</Heading>
        <NoticeCard
          title={title || t("previewEmpty")}
          place={place?.name ?? t("previewNoPlace")}
          date={date}
          compensation={compensation}
          media={
            photoUrl ? (
              // biome-ignore lint/performance/noImgElement: see above
              <img src={photoUrl} alt="" className="absolute inset-0 size-full object-cover" />
            ) : (
              <CategoryArt
                categoryKey={categoryKey ?? "OTHER"}
                label={ti("noPhoto")}
                className="absolute inset-0"
              />
            )
          }
        />
      </section>

      <section>
        <Card>
          <CardHeader>
            <CardTitle level={2}>{t("tipsTitle")}</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="m-0 list-disc pl-5 text-muted-foreground text-sm leading-relaxed">
              <li>{t(`${kind}Tip1`)}</li>
              <li>{t(`${kind}Tip2`)}</li>
              <li>{t(`${kind}Tip3`)}</li>
            </ul>
          </CardContent>
        </Card>
      </section>
    </aside>
  )
}
