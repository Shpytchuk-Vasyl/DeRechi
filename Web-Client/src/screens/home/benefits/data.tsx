import {
  ArrowRight,
  CalendarClock,
  EyeOff,
  KeyRound,
  Mail,
  MessageSquare,
  Phone,
  Send,
  ShieldCheck,
  User,
  UserRoundX,
} from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { AutoTab } from "@/components/auto-tabs"
import { Badge } from "@/components/pouf/media"

export type Benefit = AutoTab

export async function getBenefits(): Promise<Benefit[]> {
  const t = await getTranslations("home")

  const claims: (Pick<Benefit, "icon" | "tone" | "panel"> & { number: number })[] = [
    { number: 1, icon: <Send />, tone: "mint", panel: <DirectContactPanel /> },
    { number: 2, icon: <EyeOff />, tone: "pink", panel: <HiddenContactsPanel /> },
    { number: 3, icon: <CalendarClock />, tone: "yellow", panel: <WeeklyLimitPanel /> },
    {
      number: 4,
      icon: <ShieldCheck />,
      tone: "purple",
      panel: (
        <ModeratedPanel
          published={t("benefitsKit.statusPublished")}
          moderated={t("benefitsKit.statusModerated")}
        />
      ),
    },
    { number: 5, icon: <UserRoundX />, tone: "blue", panel: <NoAccountPanel /> },
  ]

  return claims.map(({ number, ...claim }) => ({
    id: number,
    title: t(`benefit${number}Title`),
    text: t(`benefit${number}Text`),
    ...claim,
  }))
}

function DirectContactPanel() {
  return (
    <div className="flex items-center gap-3 text-ink">
      <span className="cushion-field grid size-14 flex-none place-items-center rounded-pill bg-bg">
        <User className="size-6" aria-hidden />
      </span>
      <ArrowRight className="size-5 flex-none" aria-hidden />
      <div className="flex w-36 flex-col gap-2">
        <MockField>
          <MessageSquare className="size-4 text-muted-foreground" aria-hidden />
          <span className="h-2 w-16 rounded-pill bg-border" />
        </MockField>
        <MockField>
          <Mail className="size-4 text-muted-foreground" aria-hidden />
          <span className="h-2 w-20 rounded-pill bg-border" />
        </MockField>
      </div>
    </div>
  )
}

function NoAccountPanel() {
  return (
    <div className="relative flex w-44 flex-col gap-2">
      <MockField>
        <User className="size-4 text-muted-foreground" aria-hidden />
        <span className="h-2 w-20 rounded-pill bg-border" />
      </MockField>
      <MockField>
        <KeyRound className="size-4 text-muted-foreground" aria-hidden />
        <span className="font-black text-muted-foreground text-sm tracking-[3px]">••••••</span>
      </MockField>
      <Strike />
    </div>
  )
}

function HiddenContactsPanel() {
  return (
    <div className="cushion-field flex items-center gap-2 rounded-pill bg-bg px-4 py-2.5 font-black text-ink">
      <Phone className="size-4" aria-hidden />
      <span className="tabular-nums">+38067*****45</span>
      <EyeOff className="size-4 text-muted-foreground" aria-hidden />
    </div>
  )
}

function WeeklyLimitPanel() {
  return (
    <div className="flex items-center gap-3 font-black text-ink">
      <CalendarClock className="size-12" aria-hidden />
      <span className="text-6xl tracking-tight">1×</span>
    </div>
  )
}

function ModeratedPanel({ published, moderated }: { published: string; moderated: string }) {
  return (
    <div className="flex flex-wrap items-center justify-center gap-2">
      <Badge tone="up">{published}</Badge>
      <ArrowRight className="size-5 text-ink" aria-hidden />
      <Badge tone="info">{moderated}</Badge>
    </div>
  )
}

function MockField({ children }: { children: React.ReactNode }) {
  return (
    <span className="cushion-field flex items-center gap-2 rounded-control bg-bg px-3 py-2">
      {children}
    </span>
  )
}

function Strike() {
  return (
    <span
      className="pointer-events-none absolute top-1/2 -right-2 -left-2 h-1 -rotate-24 rounded-pill bg-destructive"
      aria-hidden
    />
  )
}
