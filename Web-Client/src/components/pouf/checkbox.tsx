import * as RCheck from '@radix-ui/react-checkbox'
import { cn } from 'cn'
import { useId, type Ref } from 'react'

interface CheckboxProps {
  checked: boolean | 'indeterminate'
  onChange: (checked: boolean | 'indeterminate') => void
  id?: string
  disabled?: boolean
  label?: string
  hideLabel?: boolean
  ref?: Ref<HTMLButtonElement>
  invalid?: boolean
  describedBy?: string
}

export function Checkbox({
  checked,
  onChange,
  id,
  disabled,
  label,
  hideLabel,
  ref,
  invalid,
  describedBy,
}: CheckboxProps) {
  const generatedId = useId()
  const controlId = id ?? generatedId
  return (
    <div className="pouf-checkbox-row inline-flex items-center gap-(--s2)">
      <RCheck.Root
        ref={ref}
        id={controlId}
        className={cn([
          'pouf-checkbox w-7 h-7 rounded-[9px] border-none p-0 cursor-pointer flex-none',
          'flex items-center justify-center',
          '[transition:background_160ms_ease,transform_160ms_cubic-bezier(0.23,1,0.32,1)]',
          'enabled:active:transform-[scale(0.92)]',
          'data-[state=checked]:bg-purple disabled:opacity-50 disabled:cursor-not-allowed',
        ],
        checked === 'indeterminate' ? 'bg-purple' : 'bg-bg',
        invalid ? '[box-shadow:var(--pouf-field),inset_0_0_0_3px_var(--orange)]' : 'cushion-field')}
        checked={checked === 'indeterminate' ? 'indeterminate' : checked}
        onCheckedChange={onChange}
        disabled={disabled}
        aria-label={label}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
      >
        <RCheck.Indicator className={[
            'pouf-checkbox__indicator text-[var(--on-accent)] flex [&_svg]:w-5 [&_svg]:h-5',
            'animate-[pouf-check-pop_250ms_cubic-bezier(0.23,1,0.32,1)_both]',
            'motion-reduce:[animation-name:pouf-check-fade]',
          ].join(' ')}>
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2.4}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            {checked === 'indeterminate' ? (
              <path d="M5 12h14" />
            ) : (
              <path d="M5 12l5 5L20 7" />
            )}
          </svg>
        </RCheck.Indicator>
      </RCheck.Root>
      {label && !hideLabel && (
        <label htmlFor={controlId} className="pouf-checkbox__label text-[15px] font-bold text-ink cursor-pointer">
          {label}
        </label>
      )}
    </div>
  )
}
