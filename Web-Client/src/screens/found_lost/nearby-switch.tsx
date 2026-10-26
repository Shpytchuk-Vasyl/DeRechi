"use client"

import { cx } from "class-variance-authority"
import { MapPinOff, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { useId } from "react"
import { Switch } from "@/components/pouf/controls"
import { Label } from "@/components/pouf/Input"
import { Row } from "@/components/pouf/layout"
import { type NearProblem, useNearMe } from "@/screens/found_lost/use-near-me"

export default function NearbySwitch({ active }: { active: boolean }) {
  const t = useTranslations("list")
  const id = useId()
  const { checked, locating, isPending, problem, locate, turnOff, dismissProblem } =
    useNearMe(active)

  return (
    <>
      <Row gap={2}>
        <Label htmlFor={id}>{locating ? t("nearbyLocating") : t("nearby")}</Label>
        <Switch
          id={id}
          checked={checked || locating}
          disabled={locating || isPending}
          onChange={(next) => (next ? locate() : turnOff())}
        />
      </Row>

      {problem ? (
        <NearbyProblem
          problem={problem}
          onDismiss={dismissProblem}
          className="order-last basis-full"
        />
      ) : null}
    </>
  )
}

export function NearbyProblem({
  problem,
  onDismiss,
  className,
}: {
  problem: NearProblem
  onDismiss: () => void
  className?: string
}) {
  const t = useTranslations("list")

  return (
    <div
      role="status"
      className={cx(
        "cushion-field flex items-start gap-3 rounded-control bg-bg px-4 py-3 text-sm",
        className,
      )}
    >
      <MapPinOff className="mt-0.5 size-4 shrink-0" aria-hidden />
      <p className="m-0 flex-1">
        {problem === "denied" ? t("nearbyDenied") : t("nearbyUnavailable")}
      </p>
      <button
        type="button"
        className="-m-1 rounded-pill p-1 text-muted-foreground hover:text-ink"
        aria-label={t("nearbyDismiss")}
        onClick={onDismiss}
      >
        <X className="size-4" aria-hidden />
      </button>
    </div>
  )
}
