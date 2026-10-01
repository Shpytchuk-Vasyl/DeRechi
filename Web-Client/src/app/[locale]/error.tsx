"use client"

import { useTranslations } from "next-intl"
import { Button } from "@/components/pouf/Button"
import { Stack } from "@/components/pouf/layout"
import { Heading, Text } from "@/components/pouf/text"

export default function ErrorBoundary({ reset }: { error: Error; reset: () => void }) {
  const t = useTranslations("error")

  return (
    <div className="mx-auto max-w-160 px-4 py-24 text-center">
      <Stack gap={3}>
        <Heading level={1}>{t("genericTitle")}</Heading>
        <Text size="lg" muted className="block">
          {t("genericText")}
        </Text>
      </Stack>
      <Button onClick={reset} className="mt-7">
        {t("retry")}
      </Button>
    </div>
  )
}
