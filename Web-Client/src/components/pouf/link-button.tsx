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
  target,
  children,
}: {
  href: string
  tone?: Tone
  size?: "md" | "lg"
  variant?: "solid" | "quiet"
  block?: boolean
  target?: "_blank"
  children: ReactNode
}) {
  return (
    <Link
      href={href}
      target={target}
      rel={target === "_blank" ? "noopener noreferrer" : undefined}
      className={cx(buttonClasses({ size, variant, block }), "no-underline", toneClass(tone))}
    >
      {children}
    </Link>
  )
}
