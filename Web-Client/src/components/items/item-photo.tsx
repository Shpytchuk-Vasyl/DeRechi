"use client"

import Image from "next/image"
import { useState } from "react"
import { CategoryArt } from "@/components/items/category-art"
import { fileUrl } from "@/lib/env/client"

type Props = {
  image?: string | null
  alt: string
  sizes: string
  preload?: boolean
  categoryKey: string
  fallbackLabel: string
  iconClassName?: string
}

export function ItemPhoto({
  image,
  alt,
  sizes,
  preload,
  categoryKey,
  fallbackLabel,
  iconClassName,
}: Props) {
  const src = image ? fileUrl(image) : null
  const [failed, setFailed] = useState<string | null>(null)

  if (!src || failed === src) {
    return (
      <CategoryArt
        categoryKey={categoryKey}
        label={fallbackLabel}
        className="absolute inset-0"
        iconClassName={iconClassName}
      />
    )
  }

  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes={sizes}
      className="object-cover"
      preload={preload}
      onError={() => setFailed(src)}
    />
  )
}
