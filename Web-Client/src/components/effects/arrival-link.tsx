"use client"

import type { ComponentProps, MouseEvent } from "react"
import { Link } from "@/i18n/navigation"
import { requestArrival, sectionOf } from "./arrival"

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

function isPlainClick(event: MouseEvent) {
  return (
    !event.defaultPrevented &&
    event.button === 0 &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey
  )
}
