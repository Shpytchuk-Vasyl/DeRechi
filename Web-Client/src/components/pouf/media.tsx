import { cva, cx } from 'class-variance-authority'
import type { ReactNode } from 'react'
import { toneClass, type Tone } from './tone'
import { renderIcon, type IconLike } from './Icon'
import { cn } from 'cn'

interface BlobProps {
  icon: IconLike
  tone?: Tone
  size?: 'sm' | 'md' | 'lg'
  label?: string
}

const blob = cva(
  [
    'pouf-blob inline-flex items-center justify-center flex-none text-[var(--on-accent)]',
    // Nudged up (8-4)/2: the blob's floor is thicker than its highlight, so the true centre reads low.
    'bg-[var(--tone,var(--purple))] cushion-blob [&>svg]:[transform:translateY(-2px)]',
  ],
  {
    variants: {
      size: {
        sm: 'w-11 h-11 rounded-[14px] text-[20px]',
        md: 'w-[60px] h-[60px] rounded-[18px] text-[26px]',
        lg: 'w-20 h-20 rounded-blob text-[36px]',
      },
    },
    defaultVariants: { size: 'lg' },
  },
)

export function Blob({ icon, tone = 'purple', size = 'lg', label }: BlobProps) {
  return (
    <span
      className={cx(blob({ size }), toneClass(tone))}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      {renderIcon(icon, size === 'sm' ? 'sm' : size === 'md' ? 'md' : 'lg')}
    </span>
  )
}

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  children: ReactNode
  tone?: Tone
}

export function Badge({ children, tone = 'purple', className, ...props }: BadgeProps) {
  return (
    <span
      {...props}
      className={cn(
        'pouf-badge inline-flex items-center gap-1.5 text-sm font-black',
        'text-(--on-accent) bg-(--tone,var(--purple)) rounded-pill px-1.5 py-1 [box-shadow:none]',
        'flex-none whitespace-nowrap self-start',
        toneClass(tone),
        className,
      )}
    >
      {children}
    </span>
  )
}

export function Dot({ tone = 'purple' }: { tone?: Tone }) {
  return (
    <span
      className={cx(
        'pouf-dot w-[9px] h-[9px] rounded-[50%] bg-[var(--tone,var(--purple))]',
        '[box-shadow:inset_0_-2px_0_rgba(0,0,0,0.15)] flex-none',
        toneClass(tone),
      )}
      aria-hidden="true"
    />
  )
}

export function Figure({ src, alt, width, height }: { src: string; alt: string; width: number; height: number }) {
  return (
    <img
      className="pouf-figure block max-w-[420px] w-full h-auto rounded-control bg-bg cushion-field"
      src={src}
      alt={alt}
      width={width}
      height={height}
      loading="lazy"
    />
  )
}
