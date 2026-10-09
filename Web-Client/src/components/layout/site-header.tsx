import { getTranslations } from "next-intl/server"
import { BrandMark } from "@/components/layout/brand-mark"
import { LocaleSwitcher } from "@/components/layout/locale-switcher"
import { ReportButton } from "@/components/layout/report-button"
import { Row, Spacer } from "@/components/pouf/layout"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export async function SiteHeader() {
  const t = await getTranslations("nav")

  return (
    <header className="cushion-card flex min-h-16 items-center gap-4 rounded-pill bg-surface pr-3 pb-2 pl-5">
      <Link href={paths.home} className="flex items-center gap-2 font-black text-xl no-underline">
        <BrandMark />
        DeRechi
      </Link>

      <nav className="hidden items-center md:flex" aria-label={t("menu")}>
        <Link
          href={paths.list("lost")}
          className="inline-flex min-h-10 items-center rounded-pill px-3 font-extrabold no-underline hover:bg-bg"
        >
          {t("lost")}
        </Link>
        <Link
          href={paths.list("found")}
          className="inline-flex min-h-10 items-center rounded-pill px-3 font-extrabold no-underline hover:bg-bg"
        >
          {t("found")}
        </Link>
      </nav>

      <Spacer />

      <Row gap={2} align="center" wrap={false}>
        <span className="max-md:hidden">
          <ReportButton />
        </span>
        <LocaleSwitcher />
      </Row>
    </header>
  )
}
