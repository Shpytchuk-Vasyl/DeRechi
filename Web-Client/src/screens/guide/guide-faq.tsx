"use client"

import { Accordion } from "@/components/pouf/disclosure"
import { Text } from "@/components/pouf/text"
import type { GuideFaqItem } from "@/content/guides"

export default function GuideFaq({ items }: { items: GuideFaqItem[] }) {
  return (
    <Accordion
      items={items.map((item, index) => ({
        value: `q${index + 1}`,
        title: item.q,
        children: <Text muted>{item.a}</Text>,
      }))}
    />
  )
}
