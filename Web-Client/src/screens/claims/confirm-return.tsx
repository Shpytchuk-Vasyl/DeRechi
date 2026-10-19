"use client"

import { CircleCheck, PackageCheck, Unlink } from "lucide-react"
import { useTranslations } from "next-intl"
import { useState } from "react"
import { confirmReturn } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import { ErrorNote } from "@/components/pouf/feedback"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"
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
      <Screen icon={<CircleCheck />} tone="mint" title={t("doneTitle")} text={t("doneText")}>
        <LinkButton href={paths.home} size="lg">
          {t("home")}
        </LinkButton>
      </Screen>
    )
  }

  if (state === "invalid") {
    return (
      <Screen icon={<Unlink />} tone="orange" title={t("invalidTitle")} text={t("invalidText")}>
        <LinkButton href={paths.home} size="lg" variant="quiet">
          {t("home")}
        </LinkButton>
      </Screen>
    )
  }

  return (
    <Screen icon={<PackageCheck />} tone="purple" title={t("title")} text={t("text")}>
      {state === "failed" ? <ErrorNote>{tc("error.failed")}</ErrorNote> : null}
      <Button size="lg" onClick={confirm} loading={pending}>
        {t("button")}
      </Button>
    </Screen>
  )
}

function Screen({
  icon,
  tone,
  title,
  text,
  children,
}: {
  icon: React.ReactElement
  tone: Tone
  title: string
  text: string
  children: React.ReactNode
}) {
  return (
    <div
      aria-live="polite"
      className="mx-auto flex max-w-160 flex-col items-center gap-6 px-4 py-12 text-center"
    >
      <Blob icon={icon} tone={tone} size="lg" />
      <Heading level={1} className="whitespace-normal">
        {title}
      </Heading>
      <Text size="lg" muted className="block max-w-md">
        {text}
      </Text>
      <div className="flex flex-col items-center gap-4">{children}</div>
    </div>
  )
}
