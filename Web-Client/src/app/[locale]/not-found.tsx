import { getTranslations } from "next-intl/server"
import { Dithered404 } from "@/components/errors/dithered-404"
import { Stack } from "@/components/pouf/layout"
import { LinkButton } from "@/components/pouf/link-button"
import { Heading, Text } from "@/components/pouf/text"
import { paths } from "@/i18n/paths"

export default async function NotFound() {
  const t = await getTranslations("error")

  return (
    <section className="pb-16">
      <div className="relative isolate h-[45svh] min-h-64">
        <Dithered404 />
      </div>
      <div className="mx-auto max-w-160 px-4 text-center">
        <Stack gap={3}>
          <Heading level={1}>{t("notFoundTitle")}</Heading>
          <Text size="lg" muted className="block">
            {t("notFoundJoke")}
          </Text>
          <Text muted className="block">
            {t("notFoundText")}
          </Text>
        </Stack>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <LinkButton href={paths.home}>{t("home")}</LinkButton>
          <LinkButton href={paths.list("found")} variant="quiet">
            {t("browseFound")}
          </LinkButton>
        </div>
        <Text size="sm" muted className="mt-6 hidden [@media(hover:hover)]:block">
          {t("notFoundHint")}
        </Text>
      </div>
    </section>
  )
}
