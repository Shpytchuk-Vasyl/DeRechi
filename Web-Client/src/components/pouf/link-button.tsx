import { cx } from "class-variance-authority"
import type { ReactNode } from "react"
import { type Tone, toneClass } from "@/components/pouf/tone"
import { Link } from "@/i18n/navigation"
import { buttonClasses } from "./Button"

export function LinkButton({
  href,
  tone = "purple",
  size = "md",
  variant = "solid",
  block,
  children,
}: {
  href: string
  tone?: Tone
  size?: "md" | "lg"
  variant?: "solid" | "quiet"
  block?: boolean
  children: ReactNode
}) {
  return (
    <Link
      href={href}
      className={cx(buttonClasses({ size, variant, block }), "no-underline", toneClass(tone))}
    >
      {children}
    </Link>
  )
}
