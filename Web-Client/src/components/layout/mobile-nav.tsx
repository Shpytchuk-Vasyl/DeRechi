"use client"

import { cx } from "class-variance-authority"
import { House, Plus, Building2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { ReactElement } from "react"
import { KIND_META } from "@/components/items/kind-meta"
import { navLinks } from "@/components/layout/nav-links"
import { Link, usePathname } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { cityListKind } from "@/lib/cities"

type Tab = {
  href: string
  label: string
  icon: ReactElement
  active: boolean
}

export function MobileNav() {
  const t = useTranslations("nav")
  const pathname = usePathname()
  const links = navLinks(pathname)

  const under = (prefix: string) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  const cityKind = cityListKind(pathname)
  const inLost = under(paths.list("lost")) || cityKind === "lost"
  const inFound = under(paths.list("found")) || cityKind === "found"
  const reportKind = inFound ? "found" : "lost"

  const home: Tab = links.city
    ? { href: links.city, label: t("myCity"), icon: <Building2 />, active: pathname === links.city }
    : { href: paths.home, label: t("home"), icon: <House />, active: pathname === paths.home }

  const tabs: Tab[] = [
    home,
    {
      href: links.lost,
      label: t("lost"),
      icon: <KIND_META.lost.Icon />,
      active: inLost,
    },
    {
      href: links.found,
      label: t("found"),
      icon: <KIND_META.found.Icon />,
      active: inFound,
    },
  ]

  return (
    <>
      {inFound || inLost ? (
        <Link
          href={paths.report(reportKind)}
          prefetch={false}
          aria-label={t(KIND_META[reportKind].nav.report)}
          className="cushion-control fixed right-4 bottom-[calc(100px+env(safe-area-inset-bottom,0px))] z-40 grid size-14 place-items-center rounded-pill bg-purple text-(--on-accent) transition-transform active:translate-y-0.5 md:hidden"
        >
          <Plus className="size-6 stroke-[2.75]" aria-hidden />
        </Link>
      ) : null}

      <nav className="pouf-bottomnav container mx-auto w-[calc(100%-2rem)]" aria-label={t("menu")}>
        {tabs.map((tab) => (
          <Link
            key={tab.href}
            href={tab.href}
            className={cx("pouf-tab", tab.active && "pouf-tab--active")}
            aria-current={tab.active ? "page" : undefined}
          >
            <span className="[&_svg]:size-5" aria-hidden>
              {tab.icon}
            </span>
            <span className="pouf-tab__label">{tab.label}</span>
          </Link>
        ))}
      </nav>
    </>
  )
}
