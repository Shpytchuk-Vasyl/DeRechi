import type { ReactElement } from "react"
import type { ItemKind } from "@/api/items"
import { Row } from "@/components/pouf/layout"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"

export default function ExplainerPoint({
  kind,
  icon,
  children,
}: {
  kind: ItemKind
  icon: ReactElement
  children: string
}) {
  return (
    <Row gap={3} align="center" wrap={false}>
      <Blob icon={icon} size="sm" tone={kind === "found" ? "up" : "down"} />
      <Text>{children}</Text>
    </Row>
  )
}
