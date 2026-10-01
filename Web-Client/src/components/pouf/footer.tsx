import { Link } from '@/i18n/navigation';
import type { ReactNode } from 'react'

export interface FooterColumn {
  title: string
  links: { label: string; href: string }[]
}

interface FooterProps {
  brand: ReactNode
  tagline?: string
  columns?: FooterColumn[]
}

export function Footer({ brand, tagline, columns = [] }: FooterProps) {
  return (
    <footer className="pouf-footer bg-surface rounded-card cushion-card p-7 flex flex-wrap gap-7 justify-between">
        <div className="flex flex-col gap-2 max-w-70">
          <div className="flex items-center gap-2 font-black text-xl">{brand}</div>
          {tagline && <p className="m-0 text-muted font-bold text-[15px]">{tagline}</p>}
        </div>
        <div className="flex flex-wrap gap-7">
          {columns.map((col) => (
            <nav key={col.title} className="flex flex-col gap-2" aria-label={col.title}>
              <span className="text-xs font-black tracking-[1.5px] uppercase text-muted">{col.title}</span>
              {col.links.map((l) => (
                <Link
                  key={`${l.href}-${l.label}`}
                  href={l.href}
                  className="font-extrabold no-underline hover:text-muted transition-colors"
                >
                  {l.label}
                </Link>
              ))}
            </nav>
          ))}
        </div>
     
    </footer>
  )
}
