"use client"

import type { ComponentProps, MouseEvent } from "react"
import { flyTo, isPlainClick } from "./arrival"

type Props = Omit<ComponentProps<"a">, "href"> & { section: string }

export function SectionLink({ section, onClick, ...props }: Props) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    const target = document.getElementById(section)
    if (!target || !isPlainClick(event)) return

    event.preventDefault()
    window.history.pushState(null, "", `#${section}`)
    flyTo(target)
  }

  return <a href={`#${section}`} onClick={handleClick} {...props} />
}
