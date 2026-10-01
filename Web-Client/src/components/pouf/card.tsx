import { cva } from "class-variance-authority"
import { cn } from "cn"
import type * as React from "react"
import type { ReactNode } from "react"
import { Heading, Text } from "./text"

// Padding is biased by half the lip: the cushion's floor paints inside the box.
const surface = cva("pouf-card", {
  variants: {
    // A tint replaces the white recipe instead of stacking on it; the utilities layer would win otherwise.
    tint: {
      none: "bg-surface cushion-card",
      found: "clay-tint clay-found",
      lost: "clay-tint clay-lost",
    },
    variant: {
      default:
        "rounded-card px-(--s7) pt-[calc(var(--s7)-var(--lip)/2)] pb-[calc(var(--s7)+var(--lip)/2)]",
      hero: "rounded-[40px] px-(--s7) pt-[calc(var(--s7)-var(--lip)/2)] pb-[calc(var(--s7)+var(--lip)/2)] sm:px-(--s8) sm:pt-[calc(var(--s8)-var(--lip)/2)] sm:pb-[calc(var(--s8)+var(--lip)/2)]",
      flush: "rounded-card p-0",
      tight:
        "rounded-card px-(--s4) pt-[calc(var(--s4)-var(--lip)/2)] pb-[calc(var(--s4)+var(--lip)/2)]",
    },
    motion: {
      none: "",
      lift: "[transition:transform_160ms_ease] hover:[transform:translateY(-4px)] motion-reduce:[transform:none] motion-reduce:hover:[transform:none]",
      "tilt-left":
        "[transform:rotate(-0.6deg)] [transition:transform_160ms_ease] hover:[transform:rotate(0deg)_translateY(-4px)] motion-reduce:[transform:none] motion-reduce:hover:[transform:none]",
      "tilt-right":
        "[transform:rotate(0.6deg)] [transition:transform_160ms_ease] hover:[transform:rotate(0deg)_translateY(-4px)] motion-reduce:[transform:none] motion-reduce:hover:[transform:none]",
    },
  },
  defaultVariants: { tint: "none", variant: "default", motion: "none" },
})

type SurfaceTint = "none" | "found" | "lost"
type SurfaceVariant = "default" | "hero" | "flush" | "tight"
type SurfaceMotion = "none" | "lift" | "tilt-left" | "tilt-right"

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: "default" | "sm"
  variant?: SurfaceVariant
  motion?: SurfaceMotion
  tint?: SurfaceTint
}

// shadcn's slot API on the clay surface. Slots carry no horizontal padding: the surface pads itself.
function Card({ className, size = "default", variant, motion, tint, ...props }: CardProps) {
  const resolved = variant ?? (size === "sm" ? "tight" : "default")

  return (
    <div
      data-slot="card"
      data-size={size}
      className={cn(
        surface({ tint, variant: resolved, motion }),
        resolved !== "flush" &&
          "group/card flex flex-col gap-(--card-spacing) [--card-spacing:--spacing(4)] data-[size=sm]:[--card-spacing:--spacing(3)]",
        className,
      )}
      {...props}
    />
  )
}

function CardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-header"
      className={cn(
        "group/card-header @container/card-header grid auto-rows-min items-start gap-1 has-data-[slot=card-action]:grid-cols-[1fr_auto] has-data-[slot=card-description]:grid-rows-[auto_auto]",
        className,
      )}
      {...props}
    />
  )
}

function CardTitle({ className, ...props }: React.ComponentProps<typeof Heading>) {
  return <Heading data-slot="card-title" level={3} className={className} {...props} />
}

function CardDescription({ className, ...props }: React.ComponentProps<typeof Text>) {
  return <Text data-slot="card-description" muted className={cn("block", className)} {...props} />
}

function CardAction({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-action"
      className={cn("col-start-2 row-span-2 row-start-1 self-start justify-self-end", className)}
      {...props}
    />
  )
}

function CardContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="card-content" className={className} {...props} />
}

function CardFooter({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card-footer"
      className={cn("flex items-center gap-2 border-border border-t pt-(--card-spacing)", className)}
      {...props}
    />
  )
}

interface RowCardProps {
  children: ReactNode
  onClick?: () => void
  selected?: boolean
}

// Selection is a variant, not a selector override: a selected row never emits the hover utilities.
const rowCard = cva(
  [
    "pouf-rowcard block w-full text-left [font:inherit] text-inherit border-none",
    "rounded-control px-(--s4) pt-[calc(var(--s4)-var(--lip-row)/2)] pb-[calc(var(--s4)+var(--lip-row)/2)]",
    "[transition:box-shadow_140ms_ease,transform_140ms_ease]",
  ],
  {
    variants: {
      interactive: { true: "cursor-pointer" },
      selected: {
        // Selected owns the whole text palette: headings and muted text have their own dark-mode colours.
        true: [
          "bg-purple text-[var(--on-accent)] cushion-control",
          "[&_.pouf-h1]:text-[var(--on-accent)] [&_.pouf-h2]:text-[var(--on-accent)]",
          "[&_.pouf-h3]:text-[var(--on-accent)] [&_.pouf-text]:text-[var(--on-accent)]",
        ].join(" "),
        false: "bg-surface cushion-row",
      },
    },
    compoundVariants: [
      {
        interactive: true,
        selected: false,
        className:
          "hover:cushion-row-hover hover:[transform:translateY(-1px)] active:[transform:translateY(1px)] active:cushion-control-active",
      },
    ],
    defaultVariants: { selected: false },
  },
)

function RowCard({ children, onClick, selected }: RowCardProps) {
  const className = rowCard({ interactive: !!onClick, selected: !!selected })

  if (onClick) {
    return (
      <button
        type="button"
        className={className}
        onClick={onClick}
        aria-pressed={selected}
        data-selected={selected || undefined}
      >
        {children}
      </button>
    )
  }
  return (
    <div className={className} data-selected={selected || undefined}>
      {children}
    </div>
  )
}

export { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle, RowCard }
