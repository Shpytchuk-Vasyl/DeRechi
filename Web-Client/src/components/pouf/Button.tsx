import { cva, cx } from 'class-variance-authority'
import {
  forwardRef,
  type ButtonHTMLAttributes,
  type MouseEventHandler,
  type ReactNode,
} from 'react'
import { toneClass, type Tone } from './tone'
import { cn } from 'cn'

interface ButtonProps
  extends Omit<
    ButtonHTMLAttributes<HTMLButtonElement>,
    'children' | 'style' | 'onClick' | 'type' | 'disabled'
  > {
  children: ReactNode
  onClick?: MouseEventHandler<HTMLButtonElement>
  tone?: Tone
  size?: 'sm' | 'md' | 'lg'
  variant?: 'solid' | 'quiet'
  block?: boolean
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit'
  label?: string
}

const button = cva(
  [
    "pouf-btn pouf-button-shortcut",
  ],
  {
    variants: {
      size: {
        sm: 'text-[13px] px-4 py-[9px] min-h-[38px] rounded-[14px]',
        md: 'text-[15px] px-[26px] py-[14px] min-h-12 rounded-control',
        lg: 'text-[17px] px-8 py-[18px] min-h-14 rounded-control',
      },
      variant: {
        solid:
          'text-[var(--on-accent)] bg-[var(--tone,var(--purple))] cushion-control disabled:cushion-control-active disabled:[transform:translateY(2px)]',
        quiet:
          'text-[var(--quiet-ink,var(--ink))] bg-transparent [box-shadow:inset_0_0_0_2px_rgba(201,168,255,0.55)] enabled:hover:bg-bg enabled:hover:text-ink enabled:hover:cushion-field disabled:[box-shadow:none]',
      },
      block: {
        true: 'flex w-full',
        false: 'inline-flex',
      },
      shape: {
        label: '',
        icon: '',
      },
    },
    compoundVariants: [
      { size: 'sm', shape: 'icon', className: 'w-[42px] px-0!' },
      { size: 'md', shape: 'icon', className: 'w-12 px-0!' },
      { size: 'lg', shape: 'icon', className: 'w-14 px-0!' },
    ],
    defaultVariants: { size: 'md', variant: 'solid', block: false, shape: 'label' },
  },
)

export function buttonClasses(
  opts: {
    tone?: Tone
    size?: 'sm' | 'md' | 'lg'
    variant?: 'solid' | 'quiet'
    block?: boolean
    shape?: 'label' | 'icon'
  } = {},
): string {
  const { tone = 'purple', size, variant, block, shape } = opts
  return cx(button({ size, variant, block, shape }), toneClass(tone))
}

function LoadingSpinner() {
  return (
    <span
      className="size-3.75 rounded-[50%] border-[3px] border-solid border-[color-mix(in_srgb,currentColor_24%,transparent)] border-t-current animate-[pouf-spin_620ms_linear_infinite]"
      aria-hidden="true"
    />
  )
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    children,
    onClick,
    tone = 'purple',
    size = 'md',
    variant = 'solid',
    block,
    disabled,
    loading,
    type = 'button',
    label,
    className,
    ...nativeProps
  },
  ref,
) {
  return (
    <button
      ref={ref}
      {...nativeProps}
      type={type}
      className={cn(buttonClasses({ tone, size, variant, block }), className)}
      onClick={onClick}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-label={label}
    >
      {loading && <LoadingSpinner />}
      {children}
    </button>
  )
})

interface IconButtonProps
  extends Omit<
    ButtonHTMLAttributes<HTMLButtonElement>,
    'children' | 'style' | 'onClick' | 'type' | 'disabled' | 'aria-label'
  > {
  icon: ReactNode
  label: string
  onClick?: MouseEventHandler<HTMLButtonElement>
  tone?: Tone
  size?: 'sm' | 'md' | 'lg'
  variant?: 'solid' | 'quiet'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit'
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  {
    icon,
    label,
    onClick,
    tone = 'purple',
    size = 'md',
    variant = 'quiet',
    disabled,
    loading,
    type = 'button',
    className,
    ...nativeProps
  },
  ref,
) {
  return (
    <button
      ref={ref}
      {...nativeProps}
      type={type}
      className={cn(buttonClasses({ tone, size, variant, shape: 'icon' }), className)}
      onClick={onClick}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-label={label}
      title={nativeProps.title ?? label}
    >
      {loading ? <LoadingSpinner /> : <span aria-hidden="true">{icon}</span>}
    </button>
  )
})
