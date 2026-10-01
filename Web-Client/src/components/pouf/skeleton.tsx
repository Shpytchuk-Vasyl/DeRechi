import { cn } from "cn"
import { Stack } from "./layout";

export function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("pouf-skeleton", className)}
      aria-hidden="true"
      {...props}
    />
  )
}

type SkeletonsProps = {
  variant?: "text" | "row" | "card"
  count?: number
  gap?: React.ComponentProps<typeof Stack>["gap"]
}

export function Skeletons({ variant = 'row', count = 1, gap = 3 }: SkeletonsProps) {
  return (
    <Stack gap={gap}>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className={cn('pouf-skeleton', `pouf-skeleton--${variant}`)} aria-hidden="true" />
      ))}
    </Stack>
  )
}
