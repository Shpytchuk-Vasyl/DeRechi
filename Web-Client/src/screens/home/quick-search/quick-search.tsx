import { getTranslations } from "next-intl/server"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/pouf/card"
import { Row, Stack } from "@/components/pouf/layout"
import { Text } from "@/components/pouf/text"
import HeroSearch from "../hero-search"
import CategoryChip from "./category-chip"

const POPULAR = ["DOCUMENTS", "KEYS", "WALLET", "BAGS"]

export default async function HomeQuickSearch(props: React.ComponentProps<typeof Card>) {
  const t = await getTranslations("home")
  const tc = await getTranslations("category")

  return (
    <Card {...props}>
      <CardHeader>
        <CardTitle level={2}>{t("searchTitle")}</CardTitle>
      </CardHeader>
      <CardContent>
        <Stack gap={4}>
          <HeroSearch />
          <Row gap={2} align="center" wrap>
            <Text size="sm" muted>
              {t("searchQuick")}
            </Text>
            {POPULAR.map((key) => (
              <CategoryChip key={key} category={key} label={tc(key)} />
            ))}
          </Row>
        </Stack>
      </CardContent>
    </Card>
  )
}
