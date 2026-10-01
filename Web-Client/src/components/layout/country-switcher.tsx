"use client"

import { Check, Flag } from "lucide-react"
import { useLocale } from "next-intl"
import { useCountry } from "@/components/country/country-provider"
import { Button } from "@/components/pouf/Button"
import { DropdownMenu } from "@/components/pouf/menu"
import { usePathname, useRouter } from "@/i18n/navigation"
import { countryName } from "@/lib/intl/country"

export function CountrySwitcher() {
  const { code: active, countries, setCountry } = useCountry()
  const locale = useLocale()
  const pathname = usePathname()
  const router = useRouter()

  function choose(code: string) {
    setCountry(code)
    router.replace(pathname)
    router.refresh()
  }

  return (
    <DropdownMenu
      items={countries.map(({ code }) => ({
        label: countryName(locale, code),
        icon: code === active ? <Check className="size-4" aria-hidden /> : undefined,
        onClick: () => choose(code),
      }))}
    >
      <Button variant="quiet" size="sm">
        <Flag className="size-4" aria-hidden />
        {active}
      </Button>
    </DropdownMenu>
  )
}
