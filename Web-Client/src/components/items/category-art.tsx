import { cx } from "class-variance-authority"
import {
  Backpack,
  Cat,
  FileText,
  Gem,
  Headphones,
  KeyRound,
  type LucideIcon,
  Package,
  Shirt,
  Wallet,
} from "lucide-react"
import { type Tone, toneClass } from "@/components/pouf/tone"

type Art = { icon: LucideIcon; tone: Tone }

const OTHER: Art = { icon: Package, tone: "idle" }

const ART: Record<string, Art> = {
  DOCUMENTS: { icon: FileText, tone: "blue" },
  KEYS: { icon: KeyRound, tone: "mint" },
  ELECTRONICS: { icon: Headphones, tone: "purple" },
  WALLET: { icon: Wallet, tone: "yellow" },
  ANIMALS: { icon: Cat, tone: "orange" },
  JEWELRY: { icon: Gem, tone: "purple" },
  CLOTHING: { icon: Shirt, tone: "blue" },
  BAGS: { icon: Backpack, tone: "pink" },
  OTHER,
}

export function categoryArt(key: string): Art {
  return ART[key] ?? OTHER
}

export function CategoryArt({
  categoryKey,
  label,
  className,
  iconClassName = "size-14",
}: {
  categoryKey: string
  label?: string
  className?: string
  iconClassName?: string
}) {
  const { icon: Icon, tone } = categoryArt(categoryKey)

  return (
    <span
      className={cx(
        "flex flex-col items-center justify-center gap-2 bg-[color-mix(in_srgb,var(--tone)_60%,var(--surface))]",
        toneClass(tone),
        className,
      )}
    >
      <Icon className={cx("stroke-[1.75]", iconClassName)} aria-hidden />
      {label ? <span className="font-bold text-sm">{label}</span> : null}
    </span>
  )
}
