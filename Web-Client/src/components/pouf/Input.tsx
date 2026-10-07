import { cva } from 'class-variance-authority'
import { cn } from 'cn'
import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react'

interface LabelProps {
  children: ReactNode
  htmlFor?: string
  as?: 'label' | 'legend'
  className?: string
}

export function Label({ children, htmlFor, as = 'label', className }: LabelProps) {
  const classes = cn('pouf-label text-sm font-black tracking-[0.6px]', className)
  if (as === 'legend') {
    return <legend className={classes}>{children}</legend>
  }
  return (
    <label className={classes} htmlFor={htmlFor}>
      {children}
    </label>
  )
}

interface FieldProps {
  label: string
  children: (id: string, describedBy: string | undefined) => ReactNode
  hint?: string
  error?: string
}

export function Field({ label, children, hint, error }: FieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-err` : hint ? `${id}-hint` : undefined
  return (
    <div className="pouf-field flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children(id, describedBy)}
      {hint && !error && (
        <span className="pouf-hint text-sm font-bold text-muted" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      {error && <FieldError id={`${id}-err`}>{error}</FieldError>}
    </div>
  )
}

export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <span
      className="pouf-error text-sm font-extrabold text-(--on-accent) bg-orange rounded-xl py-2 px-3 [align-self:start] max-w-full"
      id={id}
      role="alert"
    >
      {children}
    </span>
  )
}

// Shadow is per variant: same-property utilities don't cascade.
export const inputClasses = cva(
  'pouf-input font-bold text-[15px] text-ink border-none rounded-control w-full placeholder:text-muted',
  {
    variants: {
      bare: {
        false: 'bg-bg px-5 py-3.5 min-h-[52px] focus:outline-none focus-visible:outline-none disabled:opacity-55 disabled:cursor-not-allowed',
        true: 'bg-transparent flex-1 min-w-0 min-h-0 text-center px-0 pt-[6px] pb-[10px] [box-shadow:none] focus:[box-shadow:none] focus:outline-none focus-visible:outline-none disabled:opacity-100 disabled:cursor-not-allowed',
      },
      invalid: { true: '', false: '' },
      // mono owns font-family; a base font-pouf would fight it.
      mono: {
        true: "[font-family:ui-monospace,'SF_Mono',Menlo,monospace] [font-variant-numeric:tabular-nums]",
        false: 'font-pouf',
      },
    },
    compoundVariants: [
      { bare: false, invalid: false, className: 'cushion-field focus:[box-shadow:var(--pouf-field-focus)]' },
      {
        bare: false,
        invalid: true,
        className:
          '[box-shadow:var(--pouf-field),inset_0_0_0_3px_var(--orange)] focus:[box-shadow:var(--pouf-field-focus)]',
      },
    ],
    defaultVariants: { bare: false, invalid: false, mono: false },
  },
)

interface InputProps
  extends Omit<
    InputHTMLAttributes<HTMLInputElement>,
    'children' | 'className' | 'style' | 'value' | 'onChange'
  > {
  value: string
  onChange: (value: string) => void
  onBlur?: InputHTMLAttributes<HTMLInputElement>['onBlur']
  id?: string
  name?: string
  describedBy?: string
  placeholder?: string
  type?: InputHTMLAttributes<HTMLInputElement>['type']
  autoComplete?: string
  inputMode?: InputHTMLAttributes<HTMLInputElement>['inputMode']
  autoCapitalize?: string
  spellCheck?: boolean
  required?: boolean
  mono?: boolean
  invalid?: boolean
  disabled?: boolean
  label?: string
  bare?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    value,
    onChange,
    describedBy,
    type = 'text',
    mono,
    invalid,
    label,
    bare,
    ...nativeProps
  },
  ref,
) {
  return (
    <input
      ref={ref}
      {...nativeProps}
      className={inputClasses({ bare: !!bare, invalid: !!invalid, mono })}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      type={type}
      aria-invalid={invalid || undefined}
      aria-describedby={describedBy}
      aria-label={label}
    />
  )
})

interface TextareaProps
  extends Omit<
    TextareaHTMLAttributes<HTMLTextAreaElement>,
    'children' | 'className' | 'style' | 'value' | 'onChange'
  > {
  value: string
  onChange: (value: string) => void
  onBlur?: TextareaHTMLAttributes<HTMLTextAreaElement>['onBlur']
  id?: string
  name?: string
  describedBy?: string
  placeholder?: string
  autoComplete?: string
  spellCheck?: boolean
  required?: boolean
  rows?: number
  mono?: boolean
  invalid?: boolean
  disabled?: boolean
  label?: string
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  {
    value,
    onChange,
    describedBy,
    rows = 4,
    mono,
    invalid,
    label,
    ...nativeProps
  },
  ref,
) {
  return (
    <textarea
      ref={ref}
      {...nativeProps}
      className={cn(inputClasses({ invalid: !!invalid, mono }), "pouf-textarea min-h-[4lh] max-h-[10lh] field-sizing-content")}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      rows={rows}
      aria-invalid={invalid || undefined}
      aria-describedby={describedBy}
      aria-label={label}
    />
  )
})
