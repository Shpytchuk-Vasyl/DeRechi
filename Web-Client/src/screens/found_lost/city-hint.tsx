"use client"

import { cx } from "class-variance-authority"
import { X } from "lucide-react"
import { useTranslations } from "next-intl"
import type { ItemKind } from "@/api/items"
import { KIND_META } from "@/components/items/kind-meta"
import { ROAM_SIZE, useRoaming } from "@/components/mascot/mascot-roamer"
import { Znaida } from "@/components/mascot/znaida"
import { Button, buttonClasses } from "@/components/pouf/Button"
import { Text } from "@/components/pouf/text"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { pauseCityHint } from "@/lib/browser-storage"
import { NearbyProblem } from "@/screens/found_lost/nearby-switch"
import { useNearMe } from "@/screens/found_lost/use-near-me"

const BUBBLE_WIDTH = 320
const BUBBLE_ROOM = 260
const GUTTER = 16
const HEAD_TOP = 40
const TAIL_WIDTH = 28
const TAIL_HEIGHT = 16
const TAIL_INSET = 42

export default function CityHint({ kind, city }: { kind: ItemKind; city?: string }) {
  const t = useTranslations("list.cityHint")
  const tl = useTranslations("list")
  const tc = useTranslations("city")
  const tCommon = useTranslations("common")
  const { visit, hide } = useRoaming(true, { topOnly: true, stay: true, headroom: BUBBLE_ROOM })
  const { locating, isPending, problem, locate, dismissProblem } = useNearMe(false)

  if (!visit) return null

  function close() {
    pauseCityHint()
    hide()
  }

  function nearMe() {
    pauseCityHint()
    locate()
  }

  const out = visit.phase === "out"
  const width = Math.min(BUBBLE_WIDTH, window.innerWidth - 2 * GUTTER)
  const center = visit.x + ROAM_SIZE / 2
  const bubbleX = Math.min(Math.max(center - width / 2, GUTTER), window.innerWidth - GUTTER - width)
  const tailX = Math.min(Math.max(center - bubbleX, TAIL_INSET), width - TAIL_INSET)
  const { cityHint, tone } = KIND_META[kind]
  const where = city ? tc(`${city}.in`) : undefined

  return (
    <aside
      aria-label={t("label")}
      className="absolute z-40"
      style={{ left: visit.x, top: visit.y, width: ROAM_SIZE }}
    >
      <div
        className={cx(
          "absolute transition-[opacity,scale] duration-300 ease-[cubic-bezier(0.2,0.9,0.3,1.2)] [--bubble:color-mix(in_srgb,var(--purple)_16%,var(--surface))] motion-reduce:transition-none",
          out ? "scale-100 opacity-100 delay-300" : "pointer-events-none scale-90 opacity-0",
        )}
        style={{
          left: bubbleX - visit.x,
          bottom: ROAM_SIZE - HEAD_TOP + TAIL_HEIGHT - 2,
          width,
          transformOrigin: `${tailX}px 100%`,
        }}
      >
        <div className="cushion-card relative flex flex-col gap-2 rounded-card bg-surface p-4 pr-10 text-left">
          <p className="m-0 font-black leading-snug">
            {where ? t("titleCity", { in: where }) : t("titleNear")}
          </p>
          <Text size="sm" muted>
            {where ? t(cityHint.textCity, { in: where }) : t(cityHint.textNear)}
          </Text>

          <div className="flex flex-wrap gap-2">
            {city && where ? (
              <Link
                href={paths.cityList(city, kind)}
                onClick={() => pauseCityHint()}
                className={cx(buttonClasses({ size: "sm", tone }), "no-underline")}
              >
                {t(cityHint.go, { in: where })}
              </Link>
            ) : null}
            <Button
              size="sm"
              variant={city ? "quiet" : "solid"}
              loading={locating || isPending}
              onClick={nearMe}
            >
              {locating ? tl("nearbyLocating") : tl("nearby")}
            </Button>
          </div>

          {problem ? <NearbyProblem problem={problem} onDismiss={dismissProblem} /> : null}

          <button
            type="button"
            aria-label={tCommon("close")}
            onClick={close}
            className="absolute top-2.5 right-2.5 rounded-pill p-1.5 text-muted-foreground transition-colors hover:bg-surface hover:text-ink"
          >
            <X className="size-4" aria-hidden />
          </button>

          <svg
            aria-hidden
            viewBox={`0 0 ${TAIL_WIDTH} ${TAIL_HEIGHT}`}
            width={TAIL_WIDTH}
            height={TAIL_HEIGHT}
            className="absolute top-full -mt-px fill-(--bubble)"
            style={{ left: tailX - TAIL_WIDTH / 2 }}
          >
            <path d="M0 0C8 0 11 4 12.6 13.2C13 15.4 15 15.4 15.4 13.2C17 4 20 0 28 0Z" />
          </svg>
        </div>
      </div>

      <div className="relative overflow-hidden" style={{ width: ROAM_SIZE, height: ROAM_SIZE }}>
        <div
          className={cx(
            "znaida-roam znaida-roam-top absolute inset-x-0 top-0.5 motion-reduce:transition-none",
            out && "znaida-roam-out znaida-alive",
          )}
        >
          <Znaida look={visit.look} className="h-auto w-full" aria-hidden />
        </div>
      </div>
    </aside>
  )
}
