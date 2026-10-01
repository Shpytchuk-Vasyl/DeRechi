"use client"

import { CircleQuestionMark } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/pouf/Button"
import { useTour } from "@/components/tour/tour-context"

export function TourButton() {
  const t = useTranslations("nav")
  const tour = useTour()

  if (!tour?.available) {
    return null
  }

  return (
    <Button variant="quiet" size="sm" className="hidden sm:inline-flex" onClick={tour.start}>
      <CircleQuestionMark className="size-4" aria-hidden />
      {t("tour")}
    </Button>
  )
}
