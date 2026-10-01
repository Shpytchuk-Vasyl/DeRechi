"use client"

import { Check, Globe } from "lucide-react"
import { useLocale, useTranslations } from "next-intl"
import { Button } from "@/components/pouf/Button"
import { DropdownMenu } from "@/components/pouf/menu"
import { usePathname, useRouter } from "@/i18n/navigation"
import { type Locale, locales } from "@/i18n/routing"

const LANGUAGES: Record<Locale, { short: string; name: string }> = {
  en: { short: "EN", name: "English" },
  uk: { short: "УК", name: "Українська" },
  pl: { short: "PL", name: "Polski" },
  de: { short: "DE", name: "Deutsch" },
  fr: { short: "FR", name: "Français" },
}

export function LocaleSwitcher() {
  const active = useLocale() as Locale
  const pathname = usePathname()
  const router = useRouter()
  const t = useTranslations("nav")

  return (
    <DropdownMenu
      label={t("language")}
      items={locales.map((locale) => ({
        label: LANGUAGES[locale].name,
        icon: locale === active ? <Check className="size-4" aria-hidden /> : undefined,
        onClick: () => router.replace(pathname, { locale }),
      }))}
    >
      <Button variant="quiet" size="sm">
        <Globe className="size-4" aria-hidden />
        {LANGUAGES[active].short}
      </Button>
    </DropdownMenu>
  )
}
