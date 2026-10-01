import { cx } from "class-variance-authority"
import {
  ArrowRight,
  Coins,
  EyeOff,
  Globe,
  KeyRound,
  Megaphone,
  MegaphoneOff,
  Phone,
  ShieldCheck,
  User,
  UserRoundX,
} from "lucide-react"
import { getTranslations } from "next-intl/server"
import type { AutoTab } from "@/components/auto-tabs"
import { Badge } from "@/components/pouf/media"
import { SampleAmount } from "@/screens/home/sample-reward"

export type Benefit = AutoTab

export async function getBenefits(): Promise<Benefit[]> {
  const t = await getTranslations("home")

  const claims: Pick<Benefit, "icon" | "tone" | "panel">[] = [
    { icon: <Coins />, tone: "yellow", panel: <FreePanel /> },
    { icon: <UserRoundX />, tone: "blue", panel: <NoAccountPanel /> },
    { icon: <EyeOff />, tone: "pink", panel: <HiddenContactsPanel /> },
    { icon: <Globe />, tone: "mint", panel: <LanguagesPanel /> },
    {
      icon: <ShieldCheck />,
      tone: "purple",
      panel: (
        <ModeratedPanel
          published={t("benefitsKit.statusPublished")}
          moderated={t("benefitsKit.statusModerated")}
        />
      ),
    },
    { icon: <MegaphoneOff />, tone: "orange", panel: <NoAdsPanel /> },
  ]

  return claims.map((claim, index) => {
    const number = index + 1
    return {
      id: number,
      title: t(`benefit${number}Title`),
      text: t(`benefit${number}Text`),
      ...claim,
    }
  })
}

function FreePanel() {
  return (
    <span className="font-black text-6xl tracking-tight">
      <SampleAmount amount={0} />
    </span>
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

const LANGUAGE_CHIPS = [
  { code: "EN", tilt: "-rotate-6" },
  { code: "УК", tilt: "rotate-3" },
  { code: "PL", tilt: "-rotate-2" },
  { code: "DE", tilt: "rotate-6" },
  { code: "FR", tilt: "-rotate-3" },
]

function LanguagesPanel() {
  return (
    <div className="flex flex-wrap justify-center gap-2">
      {LANGUAGE_CHIPS.map((chip, index) => (
        <span key={chip.code} className={cx("rounded-pill shadow-ink/20 shadow-md", chip.tilt)}>
          <Badge tone={index % 2 ? "mint" : "blue"}>{chip.code}</Badge>
        </span>
      ))}
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

function NoAdsPanel() {
  return (
    <div className="relative grid h-20 w-44 place-items-center rounded-control border-2 border-purple/40 border-dashed text-muted-foreground">
      <Megaphone className="size-7" aria-hidden />
      <Strike />
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
