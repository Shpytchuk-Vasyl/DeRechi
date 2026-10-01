import { cva } from 'class-variance-authority'
import type { ReactNode } from 'react'

const cta = cva('pouf-cta rounded-card px-(--s7) py-(--s8) flex flex-col items-center text-center gap-(--s4) cushion-control', {
  variants: {
    tone: {
      purple: 'bg-purple text-[var(--on-accent)] [--quiet-ink:var(--on-accent)]',
      pink: 'bg-pink text-[var(--on-accent)] [--quiet-ink:var(--on-accent)]',
      blue: 'bg-blue text-[var(--on-accent)] [--quiet-ink:var(--on-accent)]',
      mint: 'bg-mint text-[var(--on-accent)] [--quiet-ink:var(--on-accent)]',
      yellow: 'bg-yellow text-[var(--on-accent)] [--quiet-ink:var(--on-accent)]',
      surface: 'bg-surface text-ink [--quiet-ink:var(--ink)]',
    },
  },
  defaultVariants: { tone: 'purple' },
})

interface CTAProps {
  title: string
  description?: string
  action: ReactNode
  tone?: 'purple' | 'pink' | 'blue' | 'mint' | 'yellow' | 'surface'
}

export function CTA({ title, description, action, tone }: CTAProps) {
  return (
    <section className={cta({ tone })}>
      <h2 className="m-0 font-black text-[clamp(28px,4vw,44px)] tracking-[-0.02em] leading-[1.05] text-current">{title}</h2>
      {description && <p className="m-0 font-bold text-[18px] text-current/70 max-w-[52ch]">{description}</p>}
      <div className="flex flex-wrap gap-(--s3) justify-center mt-(--s2)">{action}</div>
    </section>
  )
}
