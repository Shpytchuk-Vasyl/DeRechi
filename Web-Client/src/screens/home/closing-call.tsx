import { HeartHandshake, Sparkles } from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { ReactElement } from "react"
import { fetchStats } from "@/api/stats"
import { CountUp } from "@/components/effects/count-up"
import { Card, CardContent } from "@/components/pouf/card"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"

type Figure = {
  id: string
  value: number
  label: string
  icon: ReactElement
  tone: Tone
  live: boolean
}

async function ClosingCall() {
  const [t, stats] = await Promise.all([getTranslations("home"), fetchStats()])

  const figures: Figure[] = []
  if (stats && stats.returnedThisWeek > 0) {
    figures.push({
      id: "returned",
      value: stats.returnedThisWeek,
      label: t("ctaReturnedLabel", { count: stats.returnedThisWeek }),
      icon: <HeartHandshake />,
      tone: "mint",
      live: false,
    })
  }
  if (stats && stats.foundToday > 0) {
    figures.push({
      id: "today",
      value: stats.foundToday,
      label: t("ctaTodayLabel", { count: stats.foundToday }),
      icon: <Sparkles />,
      tone: "yellow",
      live: true,
    })
  }

  if (figures.length === 0) {
    return null
  }

  return (
    <div className="grid gap-6 sm:grid-cols-2">
      {figures.map((figure, index) => (
        <Card
          data-mascot-spot
          key={figure.id}
          variant="tight"
          motion={index % 2 ? "tilt-right" : "tilt-left"}
        >
          <CardContent className="flex items-center gap-5">
            <Blob icon={figure.icon} tone={figure.tone} size="lg" />
            <div className="flex min-w-0 flex-col gap-1">
              <CountUp
                value={figure.value}
                className="block font-black text-5xl tabular-nums leading-none tracking-tight"
              />
              <div className="flex items-center gap-2">
                {figure.live ? <LiveDot /> : null}
                <Text muted>{figure.label}</Text>
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

export default ClosingCall

/** The pulse beside a figure that is still moving today. */
function LiveDot() {
  return (
    <span className="relative flex size-2.5 shrink-0" aria-hidden>
      <span className="absolute inline-flex size-full animate-ping rounded-pill bg-found-foreground opacity-60 motion-reduce:animate-none" />
      <span className="relative inline-flex size-2.5 rounded-pill bg-found-foreground" />
    </span>
  )
}
