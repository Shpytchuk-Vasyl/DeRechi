import { cva, cx } from 'class-variance-authority'
import type { ReactNode } from 'react'
import { toneClass, type Tone } from './tone'
import { cn } from 'cn'

interface HeadingProps extends React.HTMLAttributes<HTMLHeadingElement> {
  children: ReactNode
  level?: 1 | 2 | 3
}

const heading = cva('font-black [text-wrap:balance] truncate', {
  variants: {
    level: {
      1: 'pouf-h1 text-[48px] tracking-[-1px] leading-[1.1]',
      2: 'pouf-h2 text-[28px] tracking-[-0.5px] leading-[1.2]',
      // Without it h3 inherits the body's 1.5 and towers beside a 44px blob.
      3: 'pouf-h3 text-[19px] tracking-[-0.2px] leading-[1.2]',
    },
  },
  defaultVariants: { level: 2 },
})

export function Heading({ children, level = 2, className, ...props }: HeadingProps) {
  const Tag = `h${level}` as const
  return <Tag className={cn(heading({ level }), className)} {...props}>
    {children}
  </Tag>
}

export function Highlight({ children, tone = 'yellow' }: { children: ReactNode; tone?: Tone }) {
  return (
    <span
      className={cx(
        'pouf-highlight inline-block px-[14px] rounded-control text-[var(--on-accent)] bg-[var(--tone,var(--yellow))]',
        '[box-shadow:inset_0_-6px_0_rgba(0,0,0,0.08)]',
        toneClass(tone),
      )}
    >
      {children}
    </span>
  )
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <div className="pouf-eyebrow text-[14px] tracking-[2px] uppercase font-extrabold text-muted">
      {children}
    </div>
  )
}

interface TextProps extends React.HTMLAttributes<HTMLSpanElement> {
  children: ReactNode
  size?: 'sm' | 'md' | 'lg'
  muted?: boolean
  num?: boolean
  mono?: boolean
  truncate?: boolean
  className?: string
}

const text = cva('pouf-text font-bold [overflow-wrap:anywhere]', {
  variants: {
    size: { md: 'text-sm', sm: 'text-xs', lg: 'text-lg' },
    muted: { true: 'text-muted' },
    num: { true: '[font-variant-numeric:tabular-nums] [font-feature-settings:"tnum"]' },
    mono: { true: "[font-family:ui-monospace,'SF_Mono',Menlo,monospace] [font-variant-numeric:tabular-nums]" },
    // Needs a block box: an inline span keeps its intrinsic width and overflows.
    truncate: { true: 'block truncate min-w-0 max-w-full' },
  },
  defaultVariants: { size: 'md' },
})

export function Text({ children, size, muted, num, mono, truncate, className, ...props }: TextProps) {
  return (
    <span dir="auto" {...props} className={cn(text({ size, muted, num, mono, truncate }), className)}>
      {children}
    </span>
  )
}
