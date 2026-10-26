import { BrandMark } from "@/components/layout/brand-mark"
import { FeedbackButton } from "@/components/layout/feedback-button"
import { HeaderNav } from "@/components/layout/header-nav"
import { LocaleSwitcher } from "@/components/layout/locale-switcher"
import { ReportButton } from "@/components/layout/report-button"
import { Row, Spacer } from "@/components/pouf/layout"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

export function SiteHeader() {
  return (
    <header className="cushion-card flex min-h-16 items-center gap-4 rounded-pill bg-surface pr-3 pb-2 pl-5">
      <Link href={paths.home} className="flex items-center gap-2 font-black text-xl no-underline">
        <BrandMark />
        DeRechi
      </Link>

      <HeaderNav />

      <Spacer />

      <Row gap={2} align="center" wrap={false}>
        <span className="max-md:hidden">
          <ReportButton />
        </span>
        <LocaleSwitcher />
        <FeedbackButton />
      </Row>
    </header>
  )
}
