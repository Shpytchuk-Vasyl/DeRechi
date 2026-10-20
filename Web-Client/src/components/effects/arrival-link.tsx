"use client"

import type { ComponentProps, MouseEvent } from "react"
import { Link } from "@/i18n/navigation"
import { isPlainClick, requestArrival, sectionOf } from "./arrival"

type Props = Omit<ComponentProps<typeof Link>, "href"> & { href: string }

export function ArrivalLink({ href, onClick, ...props }: Props) {
  const section = sectionOf(href)
  if (!section) return <Link href={href} onClick={onClick} {...props} />

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    if (isPlainClick(event)) requestArrival(section)
  }

  return <Link href={href} scroll={false} onClick={handleClick} {...props} />
}
