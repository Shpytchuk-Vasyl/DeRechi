import { cn } from 'cn'
import type { ReactNode } from 'react'
import { Blob } from './media'
import { Icon } from './Icon'
import { Stack } from './layout'
import { Text } from './text'
import type { IconLike } from './Icon'

export function Empty({ icon = 'idle', title, children }: { icon?: IconLike; title: string; children?: ReactNode }) {
  return (
    <div className="pouf-empty">
      <Blob tone="purple" size="md" icon={icon} />
      <Text>{title}</Text>
      {children && (
        <Text size="sm" muted>
          {children}
        </Text>
      )}
    </div>
  )
}


export function ErrorNote({ children, className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div {...props} className={`pouf-error-note ${className || ''}`} role="alert">
      <span className="pouf-error-note__icon">
        <Icon name="warn" size="sm" />
      </span>
      <span className="pouf-error-note__text">{children}</span>
    </div>
  )
}
