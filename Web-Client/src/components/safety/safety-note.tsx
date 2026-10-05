import { cn } from "cn"
import { type LucideIcon, ShieldCheck } from "lucide-react"
import type { ReactNode } from "react"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export function SafetyNote({
  children,
  className,
  icon: Icon = ShieldCheck,
  iconClassName = "text-found",
}: {
  children: ReactNode
  className?: string
  icon?: LucideIcon
  iconClassName?: string
}) {
  return (
    <p
      className={cn(
        "flex items-start gap-2 text-muted-foreground text-sm leading-relaxed",
        className,
      )}
    >
      <Icon className={cn("mt-0.5 size-4 flex-none", iconClassName)} aria-hidden />
      <span>{children}</span>
    </p>
  )
}

export function SafetyLink({
  children,
  newTab = false,
}: {
  children: ReactNode
  newTab?: boolean
}) {
  return (
    <Link
      href={paths.safety}
      className="whitespace-nowrap font-bold underline"
      {...(newTab ? { target: "_blank", rel: "noopener" } : {})}
    >
      {children}
    </Link>
  )
}
