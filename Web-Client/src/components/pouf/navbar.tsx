import { cn } from 'cn'
import type { ReactNode } from 'react'

export interface NavbarLink {
  label: string
  href: string
  active?: boolean
}

interface NavbarProps {
  brand: ReactNode
  links?: NavbarLink[]
  actions?: ReactNode
  label?: string
}

export function Navbar({ brand, links = [], actions, label = 'Primary' }: NavbarProps) {
  return (
    <nav
      className="pouf-navbar flex items-center gap-(--s4) h-16 pl-(--s5) pr-(--s3) rounded-pill bg-surface cushion-card"
      aria-label={label}
    >
      <div className="flex items-center gap-(--s2) font-black text-[20px] text-ink">{brand}</div>
      <div className="pouf-navbar__links flex items-center gap-[2px]">
        {links.map((l) => (
          <a
            key={l.href}
            href={l.href}
            aria-current={l.active ? 'page' : undefined}
            className={cn(
              'font-extrabold no-underline px-(--s3) py-(--s2) rounded-pill transition-colors',
              // min-h so the target reaches 24px (WCAG 2.5.8): bare anchors measured 23.
              'inline-flex items-center min-h-[24px]',
              l.active
                ? 'bg-purple text-(--on-accent)'
                : 'text-ink hover:bg-[rgba(201,168,255,0.25)]',
            )}
          >
            {l.label}
          </a>
        ))}
      </div>
      {links.length > 0 ? (
        <details className="pouf-navbar__mobile relative ml-auto">
          <summary
            className="inline-flex items-center min-h-[40px] px-(--s3) font-extrabold text-ink cursor-pointer rounded-pill hover:bg-[rgba(201,168,255,0.25)]"
            style={{ listStyle: 'none' }}
          >
            Menu
          </summary>
          <div className="absolute right-0 top-[calc(100%+12px)] z-20 min-w-[190px] flex flex-col gap-(--s1) p-(--s2) bg-surface rounded-control cushion-card">
            {links.map((link) => (
              <a
                key={link.href}
                href={link.href}
                aria-current={link.active ? 'page' : undefined}
                className={cn(
                  'font-extrabold no-underline px-(--s3) py-(--s2) rounded-control min-h-[40px] inline-flex items-center',
                  link.active
                    ? 'bg-purple text-[var(--on-accent)]'
                    : 'text-ink hover:bg-[rgba(201,168,255,0.25)]',
                )}
                onClick={(event) => event.currentTarget.closest('details')?.removeAttribute('open')}
              >
                {link.label}
              </a>
            ))}
          </div>
        </details>
      ) : null}
      {actions && (
        <div className="pouf-navbar__actions flex items-center gap-(--s2) ml-auto">
          {actions}
        </div>
      )}
    </nav>
  )
}
