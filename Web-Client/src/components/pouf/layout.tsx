import { cva } from 'class-variance-authority'
import { cn } from 'cn'
import { Slot } from 'radix-ui'
import type { ReactNode } from 'react'

type Gap = 1 | 2 | 3 | 4 | 5 | 6

const gapVariant = {
  1: 'gap-1.5',
  2: 'gap-2',
  3: 'gap-3',
  4: 'gap-4',
  5: 'gap-5',
  6: 'gap-6',
} as const

// min-w-0 on both primitives: flex items default to min-width:auto, and one wrapper without it breaks every truncate below.
const stack = cva('pouf-stack flex flex-col min-w-0', {
  variants: { gap: gapVariant },
  defaultVariants: { gap: 4 },
})

type StackProps = React.ComponentProps<'div'> & {
  gap?: Gap
  asChild?: boolean
}

export function Stack({ gap, asChild = false, className, ...props }: StackProps) {
  const Comp = asChild ? Slot.Root : 'div'
  return <Comp data-slot="stack" className={cn(stack({ gap }), className)} {...props} />
}

const row = cva('pouf-row flex flex-row min-w-0', {
  variants: {
    gap: gapVariant,
    align: { center: 'items-center', top: 'items-start' },
    justify: { start: '', center: 'justify-center', between: 'justify-between', end: 'justify-end' },
    wrap: { true: 'flex-wrap', false: 'flex-nowrap' },
  },
  defaultVariants: { gap: 4, align: 'center', justify: 'start', wrap: true },
})

interface RowProps {
  children: ReactNode
  gap?: Gap
  align?: 'center' | 'top'
  justify?: 'start' | 'center' | 'between' | 'end'
  wrap?: boolean
}

export function Row({ children, gap, align, justify, wrap }: RowProps) {
  return <div className={row({ gap, align, justify, wrap })}>{children}</div>
}

export function Spacer() {
  return <div className="pouf-spacer flex-auto min-w-0" />
}

const grid = cva('pouf-grid grid', {
  variants: {
    cols: {
      2: 'grid-cols-2 max-[900px]:grid-cols-1',
      3: 'grid-cols-3 max-[900px]:grid-cols-1',
      4: 'grid-cols-4 max-[900px]:grid-cols-1',
      sidebar:
        '[grid-template-columns:minmax(0,2fr)_minmax(0,1fr)] max-[900px]:[grid-template-columns:minmax(0,1fr)]',
    },
    gap: gapVariant,
  },
  defaultVariants: { cols: 2, gap: 4 },
})

interface GridProps {
  children: ReactNode
  cols?: 2 | 3 | 4 | 'sidebar'
  gap?: Gap
}

export function Grid({ children, cols, gap }: GridProps) {
  return <div className={grid({ cols, gap })}>{children}</div>
}

export function Shell({ children }: { children: ReactNode }) {
  return (
    <div
      className={[
        'pouf-shell grid [grid-template-columns:260px_minmax(0,1fr)] gap-(--s6) p-(--s8) max-w-[1440px] mx-auto [align-items:start]',
        'max-[900px]:[grid-template-columns:minmax(0,1fr)] max-[900px]:p-(--s4)',
        'max-[900px]:pb-[calc(96px+env(safe-area-inset-bottom,0px))]',
      ].join(' ')}
    >
      {children}
    </div>
  )
}

export function Sidebar({
  children,
  mobile = 'show',
}: {
  children: ReactNode
  mobile?: 'show' | 'hide'
}) {
  return (
    <aside
      className={[
        'pouf-sidebar sticky top-(--s8) flex flex-col gap-(--s2)',
        mobile === 'hide' ? 'max-[900px]:hidden' : 'max-[900px]:static',
      ].join(' ')}
    >
      {children}
    </aside>
  )
}
