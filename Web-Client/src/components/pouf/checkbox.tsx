import * as RCheck from '@radix-ui/react-checkbox'
import { cn } from 'cn'
import { useId, type ReactNode, type Ref } from 'react'
import { FieldError } from './Input'

interface CheckboxProps {
  checked: boolean | 'indeterminate'
  onChange: (checked: boolean | 'indeterminate') => void
  id?: string
  disabled?: boolean
  label?: ReactNode
  hideLabel?: boolean
  ref?: Ref<HTMLButtonElement>
  error?: string
}

export function Checkbox({
  checked,
  onChange,
  id,
  disabled,
  label,
  hideLabel,
  ref,
  error,
}: CheckboxProps) {
  const generatedId = useId()
  const controlId = id ?? generatedId
  const errorId = `${controlId}-err`
  return (
    <div className="pouf-checkbox-field inline-flex flex-col gap-2">
      <div className="pouf-checkbox-row inline-flex items-start gap-2">
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
          error ? '[box-shadow:var(--pouf-field),inset_0_0_0_3px_var(--orange)]' : 'cushion-field')}
          checked={checked === 'indeterminate' ? 'indeterminate' : checked}
          onCheckedChange={onChange}
          disabled={disabled}
          aria-label={hideLabel && typeof label === 'string' ? label : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
        >
          <RCheck.Indicator className={[
              'pouf-checkbox__indicator text-(--on-accent) flex [&_svg]:w-5 [&_svg]:h-5',
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
          <label htmlFor={controlId} className="pouf-checkbox__label text-sm leading-5 pt-1 font-bold cursor-pointer">
            {label}
          </label>
        )}
      </div>
      {error && <FieldError id={errorId}>{error}</FieldError>}
    </div>
  )
}
