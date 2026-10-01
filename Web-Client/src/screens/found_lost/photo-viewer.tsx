"use client"

import * as Dialog from "@radix-ui/react-dialog"
import { Maximize2, X } from "lucide-react"
import Image from "next/image"
import { type ReactNode, useState } from "react"
import { fileUrl } from "@/lib/env/client"

type Props = {
  image?: string | null
  alt: string
  openLabel: string
  closeLabel: string
  children: ReactNode
}

export default function PhotoViewer({ image, alt, openLabel, closeLabel, children }: Props) {
  const [open, setOpen] = useState(false)

  if (!image) {
    return children
  }

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger asChild>
        <button
          type="button"
          aria-label={openLabel}
          className="group absolute inset-0 cursor-zoom-in focus-visible:outline-3 focus-visible:outline-ring focus-visible:-outline-offset-1"
        >
          {children}
          <span
            aria-hidden
            className="absolute right-3 bottom-3 grid size-9 place-items-center rounded-pill bg-black/45 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 max-sm:opacity-100"
          >
            <Maximize2 className="size-4" />
          </span>
        </button>
      </Dialog.Trigger>

      <Dialog.Portal>
        <Dialog.Overlay className="data-[state=open]:fade-in-0 fixed inset-0 z-90 bg-black/90 data-[state=open]:animate-in" />
        <Dialog.Content
          aria-describedby={undefined}
          onClick={() => setOpen(false)}
          className="data-[state=open]:zoom-in-95 fixed inset-0 z-90 cursor-zoom-out p-4 outline-none data-[state=open]:animate-in sm:p-10"
        >
          <Dialog.Title className="sr-only">{alt}</Dialog.Title>
          <div className="relative size-full">
            <Image src={fileUrl(image)} alt={alt} fill sizes="100vw" className="object-contain" />
          </div>
          <Dialog.Close
            aria-label={closeLabel}
            className="absolute top-4 right-4 grid size-11 place-items-center rounded-pill bg-white/15 text-white transition-colors hover:bg-white/25"
          >
            <X className="size-5" />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
