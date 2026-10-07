"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { cx } from "class-variance-authority"
import { useTranslations } from "next-intl"
import { type SubmitEvent, useRef, useState } from "react"
import { type FieldErrors, FormProvider, useForm } from "react-hook-form"
import type { Category, ItemKind } from "@/api/items"
import { createNotice } from "@/app/actions/report"
import StepIndicator from "@/components/form/step-indicator"
import { Button } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { ErrorNote } from "@/components/pouf/feedback"
import { Skeleton } from "@/components/pouf/skeleton"
import { toast } from "@/components/pouf/toaster"
import { Tour } from "@/components/tour/tour"
import { usePhoto } from "@/hooks/use-photo"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { draftSchema, type ReportDraft, todayIso } from "@/schema/report-schema"
import PreviewAside from "./preview-aside"
import { LAST, STEPS } from "./steps/report-steps"
import StepContacts from "./steps/step-contacts"
import StepDetails from "./steps/step-details"
import StepWhereWhen from "./steps/step-where-when"
import { useReportDraft } from "./use-report-draft"

type Props = {
  kind: ItemKind
  categories: Category[]
  maxUploadBytes: number
  compact?: boolean
}

export default function ReportForm({ kind, categories, maxUploadBytes, compact = false }: Props) {
  const t = useTranslations("form")
  const tu = useTranslations("upload")
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
  const {
    handleSubmit,
    trigger,
    formState: { isSubmitting },
  } = form

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
      router.replace(paths.item(kind, result.id))
      return
    }

    setFailure(t(`error.${result.reason}`))
  }, onInvalid)

  function onSubmit(event: SubmitEvent<HTMLFormElement>) {
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

  const Shell = compact ? "div" : Card

  const panel = (index: number) =>
    cx("flex flex-col gap-6", index !== step && "hidden", index >= opened && "hidden")

  return (
    <FormProvider {...form}>
      <form
        onSubmit={onSubmit}
        noValidate
        className={
          compact
            ? "flex min-h-0 flex-1 flex-col gap-4"
            : "grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px]"
        }
      >
        {compact ? null : <Tour id="report" />}

        <Shell className={cx("flex h-fit flex-col gap-5", compact && "min-h-0 flex-1")}>
          <StepIndicator
            steps={STEPS.map((it) => t(`steps.${it.label}`))}
            current={step}
            onSelect={go}
          />

          <div
            ref={body}
            className={cx(compact && "-mx-4 min-h-0 flex-1 overflow-y-auto px-4 py-1")}
          >
            <section className={panel(0)}>
              <StepDetails kind={kind} categories={categories} />
            </section>

            {opened > 1 ? (
              <section className={panel(1)}>
                <StepWhereWhen
                  kind={kind}
                  categories={categories}
                  maxUploadBytes={maxUploadBytes}
                  photo={photo}
                />
              </section>
            ) : null}

            {opened > 2 ? (
              <section className={panel(2)}>
                <StepContacts />
              </section>
            ) : null}
          </div>

          {failure ? (
            <div className="shrink-0">
              <ErrorNote>{failure}</ErrorNote>
            </div>
          ) : null}

          <div
            className={cx(
              "flex shrink-0 flex-wrap items-center gap-3 border-border border-t pt-4",
              compact && "-mx-4 px-4",
            )}
          >
            {step === 0 ? (
              <Button variant="quiet" onClick={cancel}>
                {t("cancel")}
              </Button>
            ) : (
              <Button variant="quiet" onClick={() => go(step - 1)}>
                {t("back")}
              </Button>
            )}
            <span className="flex-1" />
            <Button type="submit" loading={step === LAST && isSubmitting}>
              {step < LAST
                ? t("next")
                : photo.uploading
                  ? tu("uploading")
                  : isSubmitting
                    ? t("publishing")
                    : t("publish")}
            </Button>
          </div>
        </Shell>

        {compact ? null : <PreviewAside kind={kind} categories={categories} photoUrl={photo.url} />}
      </form>
    </FormProvider>
  )
}

export function ReportFormSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <Skeleton className="h-4.5 w-2/3" />
      {FIELD_SLOTS.map((slot) => (
        <div key={slot} className="flex flex-col gap-2">
          <Skeleton className="h-3.5 w-28" />
          <Skeleton className="h-[52px]" />
        </div>
      ))}
    </div>
  )
}

const FIELD_SLOTS = ["title", "category", "description"]
