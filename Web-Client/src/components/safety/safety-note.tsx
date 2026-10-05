import { cn } from "cn"
import { ShieldCheck } from "lucide-react"
import type { ReactNode } from "react"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export function SafetyNote({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p
      className={cn(
        "flex items-start gap-2 text-muted-foreground text-sm leading-relaxed",
        className,
      )}
    >
      <ShieldCheck className="mt-0.5 size-4 flex-none text-mint" aria-hidden />
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
      className="font-bold underline"
      {...(newTab ? { target: "_blank", rel: "noopener" } : {})}
    >
      {children}
    </Link>
  )
}
