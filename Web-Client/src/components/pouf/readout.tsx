import type { ReactNode } from 'react'
import { Card } from './card'
import { Blob } from './media'
import { Text } from './text'
import type { IconLike } from './Icon'
import type { Tone } from './tone'

export function Stat({ label, value, icon, tone }: { label: string; value: string; icon: IconLike; tone: Tone }) {
  return (
    <Card variant="tight">
      <div className="pouf-stat flex items-center gap-(--s3)">
        <Blob tone={tone} size="sm" icon={icon} />
        <div className="pouf-stat__text flex flex-col gap-[2px] min-w-0">
          <span className="pouf-stat__label text-[12px] font-extrabold tracking-[1.4px] uppercase text-muted whitespace-nowrap">
            {label}
          </span>
          <span
            className='pouf-stat__value text-[26px] font-black leading-[1.05] tracking-[-0.5px] text-ink [font-variant-numeric:tabular-nums] [font-feature-settings:"tnum"]'
            dir="auto"
          >
            {value}
          </span>
        </div>
      </div>
    </Card>
  )
}

interface MetricProps {
  label: string
  /** null is unknown and renders as a dash, never as 0. */
  value: ReactNode | null
  num?: boolean
  mono?: boolean
}

export function Metric({ label, value, num = true, mono }: MetricProps) {
  return (
    <div className="pouf-metric flex flex-col gap-(--s1) min-w-0 whitespace-nowrap [.pouf-row>&]:flex-[1_1_0] [.pouf-row>&]:min-w-max">
      <Text size="sm" muted>
        {label}
      </Text>
      <Text num={num} mono={mono}>
        {value ?? '–'}
      </Text>
    </div>
  )
}
