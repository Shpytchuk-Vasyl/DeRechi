"use client"

import { cx } from "class-variance-authority"
import { Check } from "lucide-react"
import { useTranslations } from "next-intl"
import { Fragment } from "react"
import { Text } from "../pouf/text"

type Props = {
  steps: string[]
  current: number
  onSelect: (index: number) => void
}

type State = "done" | "active" | "upcoming"

export default function StepIndicator({ steps, current, onSelect }: Props) {
  const t = useTranslations("form")

  const stateOf = (index: number): State =>
    index < current ? "done" : index === current ? "active" : "upcoming"

  return (
    <nav aria-label={t("stepOf", { current: current + 1, total: steps.length })}>
      <ol className="flex items-center gap-2 overflow-x-auto">
        {steps.map((label, index) => (
          <Fragment key={label}>
            {index > 0 ? <Connector reached={stateOf(index) !== "upcoming"} /> : null}
            <Step
              label={label}
              number={index + 1}
              state={stateOf(index)}
              onSelect={() => onSelect(index)}
            />
          </Fragment>
        ))}
      </ol>
    </nav>
  )
}

function Connector({ reached }: { reached: boolean }) {
  return (
    <li
      aria-hidden
      className={cx(
        "h-0.5 min-w-4 flex-1 rounded-pill transition-colors",
        reached ? "bg-primary" : "bg-border",
      )}
    />
  )
}

function Step({
  label,
  number,
  state,
  onSelect,
}: {
  label: string
  number: number
  state: State
  onSelect: () => void
}) {
  const t = useTranslations("form")
  const active = state === "active"

  return (
    <li className="flex shrink-0">
      <button
        type="button"
        disabled={state !== "done"}
        onClick={onSelect}
        aria-current={active ? "step" : undefined}
        aria-label={t("stepLabel", { number, title: label })}
        className="group flex items-center gap-2 rounded-pill disabled:cursor-default"
      >
        <StepBadge number={number} state={state} />
        <Text muted={state === "upcoming"} size="md" className={active ? "inline" : "hidden"}>
          {label}
        </Text>
      </button>
    </li>
  )
}

function StepBadge({ number, state }: { number: number; state: State }) {
  return (
    <span
      className={cx(
        "grid size-7 place-items-center rounded-pill font-black text-sm transition-colors",
        state === "done" && "bg-mint text-(--on-accent) group-hover:bg-found",
        state === "active" && "bg-primary text-primary-foreground shadow-md",
        state === "upcoming" && "bg-bg text-muted-foreground",
      )}
    >
      {state === "done" ? <Check className="size-4 stroke-3" aria-hidden /> : number}
    </span>
  )
}
