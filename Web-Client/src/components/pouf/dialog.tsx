"use client"

import { cn } from "cn"
import { XIcon } from "lucide-react"
import { useTranslations } from "next-intl"
import { Dialog as DialogPrimitive } from "radix-ui"
import type * as React from "react"
import { Button, IconButton } from "./Button"
import { Heading, Text } from "./text"

function Dialog({ ...props }: React.ComponentProps<typeof DialogPrimitive.Root>) {
  return <DialogPrimitive.Root data-slot="dialog" {...props} />
}

function ContentInner({ size = "lg", className, ...props }: React.ComponentProps<typeof DialogPrimitive.Content> & { size: "md" | "lg" }) {
  return  <DialogPrimitive.Content
  data-slot="dialog-content"
  className={cn(
          "pouf-dialog",
          size === "lg" ? "pouf-dialog--lg" : "",
          className,
        )}
        {...props}
      />
}

function DialogTrigger({ ...props }: React.ComponentProps<typeof DialogPrimitive.Trigger>) {
  return <DialogPrimitive.Trigger data-slot="dialog-trigger" {...props} />
}

function DialogPortal({ ...props }: React.ComponentProps<typeof DialogPrimitive.Portal>) {
  return <DialogPrimitive.Portal data-slot="dialog-portal" {...props} />
}

function DialogClose({ ...props }: React.ComponentProps<typeof DialogPrimitive.Close>) {
  return <DialogPrimitive.Close data-slot="dialog-close" {...props} />
}

function DialogOverlay({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Overlay>) {
  return (
    <DialogPrimitive.Overlay
      data-slot="dialog-overlay"
      className={cn(
        "pouf-overlay",
        className,
      )}
      {...props}
    />
  )
}

function DialogBody({ children, className, ...props }: React.ComponentProps<"div">) {
  return (
    <div data-slot="dialog-body" className={cn("pouf-dialog__body", className)} {...props}>
      {children}
    </div>
  )
}

function DialogContent({
  children,
  showCloseButton = true,
  size = "lg",
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & {
  showCloseButton?: boolean
  size?: "md" | "lg"
}) {
  const t = useTranslations("common")

  return (
    <DialogPortal>
      <DialogOverlay />
      <ContentInner
        size={size}
        {...props}
      >
        {children}
        {showCloseButton && (
          <DialogPrimitive.Close data-slot="dialog-close" asChild>
            <IconButton 
            icon={<XIcon />}
            variant="quiet" 
            className="absolute top-2 right-2"
            size="sm" 
            label={t("close")} />
          </DialogPrimitive.Close>
        )}
      </ContentInner>
    </DialogPortal>
  )
}

function DialogHeader({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div data-slot="dialog-header" className={cn("pouf-dialog__head flex flex-col gap-1", className)} {...props} />
  )
}

function DialogFooter({
  className,
  showCloseButton = false,
  children,
  ...props
}: React.ComponentProps<"div"> & {
  showCloseButton?: boolean
}) {
  const t = useTranslations("common")

  return (
    <div
      data-slot="dialog-footer"
      className={cn(
        "-mx-4 -mb-4 flex flex-col-reverse gap-2 rounded-b-xl border-t bg-muted/50 p-4 sm:flex-row sm:justify-end",
        className,
      )}
      {...props}
    >
      {children}
      {showCloseButton && (
        <DialogPrimitive.Close asChild>
          <Button variant="quiet" size="sm">{t("close")}</Button>
        </DialogPrimitive.Close>
      )}
    </div>
  )
}

function DialogTitle({ className, children, ...props }: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      data-slot="dialog-title"
      asChild
      {...props}
    >
      <Heading level={3} className={className}>
        {children}
      </Heading>
    </DialogPrimitive.Title>
  )
}

function DialogDescription({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Description>) {
  return (
    <DialogPrimitive.Description
      data-slot="dialog-description"
      asChild
      {...props}
    >
      <Text size="sm" muted className={className}>{children}</Text>
    </DialogPrimitive.Description>
  )
}

export {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogOverlay,
  DialogPortal,
  DialogTitle,
  DialogTrigger,
  DialogBody,
  ContentInner
}
