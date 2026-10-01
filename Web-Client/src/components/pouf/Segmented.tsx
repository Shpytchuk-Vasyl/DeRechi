import { cn } from 'cn'
import { type Tone } from './tone'
import { buttonClasses } from './Button'

export interface SegmentedOption<T extends string> {
  value: T
  label: string
}

interface SegmentedProps<T extends string> {
  value: T
  onChange: (value: T) => void
  options: SegmentedOption<T>[]
  label: string
  tone?: Tone
}

export function Segmented<T extends string>({ value, onChange, options, label, tone = 'blue' }: SegmentedProps<T>) {
  return (
    <div className="pouf-seg inline-flex gap-(--s2)" role="group" aria-label={label}>
      {options.map((o) => {
        const on = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            // Pressed-in depth marks the selection, not hue; aria-pressed carries it for readers.
            className={cn(buttonClasses({ size: 'sm', tone }), 'aria-pressed:transform-[translateY(2px)] aria-pressed:cushion-control-active')}
            aria-pressed={on}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
