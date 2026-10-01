import { getTranslations } from "next-intl/server"
import { AutoTabs } from "@/components/auto-tabs"
import { getBenefits } from "./data"

async function Benefits() {
  const t = await getTranslations("home")
  const benefits = await getBenefits()

  return (
    <section data-mascot-spot aria-label={t("benefitsTitle")}>
      <AutoTabs tabs={benefits} />
    </section>
  )
}

export default Benefits
