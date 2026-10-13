"use client"

import { Select } from "@/components/pouf/controls"
import { useRouter } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"

type Props = {
  cities: { slug: string; name: string }[]
  label: string
}

export default function CityPicker({ cities, label }: Props) {
  const router = useRouter()

  return (
    <Select
      value=""
      onChange={(slug) => router.push(paths.city(slug))}
      options={cities.map((city) => ({ value: city.slug, label: city.name }))}
      placeholder={label}
      label={label}
    />
  )
}
