"use client"

import { useTranslations } from "next-intl"
import { navLinks } from "@/components/layout/nav-links"
import { Link, usePathname } from "@/i18n/navigation"

const LINK =
  "inline-flex min-h-10 items-center rounded-pill px-3 font-extrabold no-underline hover:bg-bg"

export function HeaderNav() {
  const t = useTranslations("nav")
  const links = navLinks(usePathname())

  return (
    <nav className="hidden items-center md:flex" aria-label={t("menu")}>
      <Link href={links.lost} className={LINK}>
        {t("lost")}
      </Link>
      <Link href={links.found} className={LINK}>
        {t("found")}
      </Link>
    </nav>
  )
}
