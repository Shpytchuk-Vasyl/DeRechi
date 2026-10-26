"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useTranslations } from "next-intl"
import { type SubmitEvent, useRef, useState } from "react"
import { type FieldErrors, useForm } from "react-hook-form"
import type { ItemKind } from "@/api/items"
import { createNotice } from "@/app/actions/report"
import { toast } from "@/components/pouf/toaster"
import { usePhoto } from "@/hooks/use-photo"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { markJustPublished } from "@/lib/browser-storage"
import { draftSchema, type ReportDraft, todayIso } from "@/schema/report-schema"
import { LAST, STEPS } from "./steps/report-steps"
import { useReportDraft } from "./use-report-draft"

export function useReportWizard(kind: ItemKind, compact: boolean) {
  const t = useTranslations("form")
  const router = useRouter()
  const body = useRef<HTMLDivElement>(null)
  const [step, setStep] = useState(0)
  const [opened, setOpened] = useState(1)
  const [failure, setFailure] = useState<string | null>(null)
  const photo = usePhoto()

  const form = useForm<ReportDraft>({
    resolver: zodResolver(draftSchema(kind)),
    defaultValues: {
      title: "",
      description: "",
      date: todayIso(),
      categoryId: "",
      consent: false,
    },
  })
  const { handleSubmit, trigger } = form

  function go(index: number) {
    setStep(index)
    setOpened((count) => Math.max(count, index + 1))
    body.current?.scrollTo({ top: 0 })
  }

  const draft = useReportDraft({
    kind,
    step,
    watch: form.watch,
    reset: form.reset,
    onRestore: (saved) => {
      go(saved)
      toast.info(t("draftRestored"))
    },
  })

  function photoMissing(): boolean {
    if (kind === "found" && !photo.file) {
      photo.setError(t("error.photoRequired"))
      return true
    }
    return false
  }

  async function next() {
    const valid = await trigger(STEPS[step].fields, { shouldFocus: true })
    const photoOk = step !== 1 || !photoMissing()
    if (valid && photoOk) go(step + 1)
  }

  function onInvalid(invalid: FieldErrors<ReportDraft>) {
    const index = STEPS.findIndex((it) =>
      it.fields.some((field) =>
        field
          .split(".")
          .reduce<unknown>(
            (node, key) => (node as Record<string, unknown> | undefined)?.[key],
            invalid,
          ),
      ),
    )
    if (index >= 0 && index !== step) go(index)
  }

  const publish = handleSubmit(async (values) => {
    setFailure(null)
    photo.setError(null)

    if (photoMissing()) {
      go(1)
      return
    }

    const image = await photo.upload()
    if (image === null) {
      go(1)
      return
    }

    const result = await createNotice(kind, { ...values, image })
    if (result.ok) {
      draft.clear()
      markJustPublished(kind, result.id)
      router.replace(paths.item(kind, result.id))
      return
    }

    setFailure(t(`error.${result.reason}`))
  }, onInvalid)

  function submit(event: SubmitEvent<HTMLFormElement>) {
    if (step < LAST) {
      event.preventDefault()
      void next()
      return
    }
    void publish(event)
  }

  function cancel() {
    if (compact) router.back()
    else router.push(paths.list(kind))
  }

  return { form, photo, body, step, opened, go, failure, submit, cancel }
}
