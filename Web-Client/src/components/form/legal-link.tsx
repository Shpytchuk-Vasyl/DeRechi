import type { ReactNode } from "react"
import { Link } from "@/i18n/navigation"
import type { paths } from "@/i18n/paths"

type Props = {
  href: {
    pathname: typeof paths.terms | typeof paths.privacy
    query: { country: string }
    hash?: string
  }
  children: ReactNode
}

export function LegalLink({ href, children }: Props) {
  return (
    <Link href={href} target="_blank" rel="noopener" className="font-bold underline">
      {children}
    </Link>
  )
}
