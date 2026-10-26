import { getTranslations } from "next-intl/server"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/pouf/card"
import { Row, Stack } from "@/components/pouf/layout"
import { Text } from "@/components/pouf/text"
import HeroSearch from "../hero-search"
import CategoryChip from "./category-chip"

const POPULAR = ["DOCUMENTS", "KEYS", "WALLET", "BAGS"]

type Props = React.ComponentProps<typeof Card> & { city?: string }

export default async function HomeQuickSearch({ city, ...props }: Props) {
  const t = await getTranslations("home")
  const tc = await getTranslations("category")

  return (
    <Card {...props}>
      <CardHeader>
        <CardTitle level={2}>{t("searchTitle")}</CardTitle>
      </CardHeader>
      <CardContent>
        <Stack gap={4}>
          <HeroSearch city={city} />
          <Row gap={2} align="center" wrap>
            <Text size="sm" muted>
              {t("searchQuick")}
            </Text>
            {POPULAR.map((key) => (
              <CategoryChip key={key} category={key} label={tc(key)} city={city} />
            ))}
          </Row>
        </Stack>
      </CardContent>
    </Card>
  )
}
