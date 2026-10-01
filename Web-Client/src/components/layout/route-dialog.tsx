"use client"

import { cn } from "cn"
import { useState } from "react"
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/pouf/dialog"
import { useRouter } from "@/i18n/navigation"

type Props = {
  title: string
  description?: string
  headerHidden?: boolean
  className?: string
  children: React.ReactNode
}

export function RouteDialog({ title, description, headerHidden, className, children }: Props) {
  const router = useRouter()
  const [open, setOpen] = useState(true)

  function onOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      router.back()
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className={cn("max-h-[75vh] md:max-h-[90vh]", className)}
        onInteractOutside={(event) => {
          if ((event.target as Element | null)?.closest?.(".pac-container")) {
            event.preventDefault()
          }
        }}
      >
        <DialogHeader className={headerHidden ? "sr-only" : "shrink-0"}>
          <DialogTitle>{title}</DialogTitle>
          {description ? <DialogDescription>{description}</DialogDescription> : null}
        </DialogHeader>
        <DialogBody>{children}</DialogBody>
      </DialogContent>
    </Dialog>
  )
}
