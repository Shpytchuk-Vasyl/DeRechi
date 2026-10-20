"use client"

import { requestArrival, sectionOf } from "@/components/effects/arrival"
import { Select } from "@/components/pouf/controls"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

type Props = {
  cities: { slug: string; name: string }[]
  label: string
  hash?: string
}

export default function CityPicker({ cities, label, hash = "" }: Props) {
  const router = useRouter()

  function open(slug: string) {
    const href = `${paths.city(slug)}${hash}`
    const section = sectionOf(href)
    if (section) requestArrival(section)
    router.push(href, { scroll: !section })
  }

  return (
    <Select
      value=""
      onChange={open}
      options={cities.map((city) => ({ value: city.slug, label: city.name }))}
      placeholder={label}
      label={label}
    />
  )
}
