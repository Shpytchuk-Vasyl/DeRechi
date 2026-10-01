import type { ReactElement } from "react"
import { Stack } from "@/components/pouf/layout"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"

type Props = {
  title: string
  description?: string
  icon?: ReactElement
  tone?: Tone
}

export function PageHeader({ title, description, icon, tone = "purple" }: Props) {
  return (
    <div className="mb-7 flex items-center justify-between gap-6">
      <Stack gap={2} className="max-w-2xl">
        <Heading level={1}>{title}</Heading>
        {description ? (
          <Text size="lg" muted>
            {description}
          </Text>
        ) : null}
      </Stack>
      {icon ? (
        <div className="hidden shrink-0 sm:block">
          <Blob icon={icon} tone={tone} size="lg" />
        </div>
      ) : null}
    </div>
  )
}
