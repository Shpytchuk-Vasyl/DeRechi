"use client"

import { CircleCheck, PackageCheck, Unlink } from "lucide-react"
import { useTranslations } from "next-intl"
import { useState } from "react"
import { confirmReturn } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/pouf/card"
import { ErrorNote } from "@/components/pouf/feedback"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { paths } from "@/i18n/paths"

type State = "idle" | "done" | "invalid" | "failed"

export default function ConfirmReturn({ token }: { token: string }) {
  const t = useTranslations("claim.confirm")
  const tc = useTranslations("claim")
  const [state, setState] = useState<State>("idle")
  const [pending, setPending] = useState(false)

  async function confirm() {
    setPending(true)
    try {
      const result = await confirmReturn(token)
      setState(result.ok ? "done" : result.reason === "notFound" ? "invalid" : "failed")
    } catch {
      setState("failed")
    } finally {
      setPending(false)
    }
  }

  if (state === "done") {
    return (
      <Shell icon={<CircleCheck />} tone="mint" title={t("doneTitle")} text={t("doneText")}>
        <LinkButton href={paths.home}>{t("home")}</LinkButton>
      </Shell>
    )
  }

  if (state === "invalid") {
    return (
      <Shell icon={<Unlink />} tone="orange" title={t("invalidTitle")} text={t("invalidText")}>
        <LinkButton href={paths.home} variant="quiet">
          {t("home")}
        </LinkButton>
      </Shell>
    )
  }

  return (
    <Shell icon={<PackageCheck />} tone="purple" title={t("title")} text={t("text")}>
      {state === "failed" ? <ErrorNote>{tc("error.failed")}</ErrorNote> : null}
      <Button size="lg" onClick={confirm} loading={pending}>
        {t("button")}
      </Button>
    </Shell>
  )
}

function Shell({
  icon,
  tone,
  title,
  text,
  children,
}: {
  icon: React.ReactElement
  tone: "mint" | "orange" | "purple"
  title: string
  text: string
  children: React.ReactNode
}) {
  return (
      <Card className="items-center text-center mx-auto max-w-xl">
        <CardHeader >
        <Blob icon={icon} tone={tone} size="md" />
        <CardTitle>
          {title}
        </CardTitle>
        <CardDescription>
          {text}
        </CardDescription>
        </CardHeader>
        <CardContent className="mt-2 flex flex-col items-center gap-4">{children}</CardContent>
      </Card>
  )
}
