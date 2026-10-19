import { IdCard, KeyRound, PawPrint, Smartphone, Wallet } from "lucide-react"
import type { ReactElement } from "react"
import type { Tone } from "@/components/pouf/tone"
import type { GuideSlug } from "@/content/guides"

export const GUIDE_ICONS: Record<
  GuideSlug,
  { icon: ReactElement<{ className?: string }>; tone: Tone }
> = {
  documents: { icon: <IdCard />, tone: "pink" },
  keys: { icon: <KeyRound />, tone: "yellow" },
  wallet: { icon: <Wallet />, tone: "orange" },
  electronics: { icon: <Smartphone />, tone: "blue" },
  animals: { icon: <PawPrint />, tone: "mint" },
}
