import { cn } from "cn"
import type { ReactElement } from "react"
import { Row } from "@/components/pouf/layout"
import { Text } from "@/components/pouf/text"

type Props = {
  icon: ReactElement
  children: string
  faint?: boolean
  truncate?: boolean
}

export function MetaRow({ icon, children, faint = false, truncate = false }: Props) {
  return (
    <Row gap={1} align="center" wrap={false}>
      <span className={cn("shrink-0 text-muted [&>svg]:size-3.5", faint && "opacity-70")} aria-hidden>
        {icon}
      </span>
      <Text size="sm" muted truncate={truncate} className={cn(faint && "opacity-70")}>
        {children}
      </Text>
    </Row>
  )
}
