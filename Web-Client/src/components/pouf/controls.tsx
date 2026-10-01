import * as RSelect from '@radix-ui/react-select'
import * as RSwitch from '@radix-ui/react-switch'
import * as RTooltip from '@radix-ui/react-tooltip'
import * as RAlert from '@radix-ui/react-alert-dialog'
import * as RDialog from './dialog'
import * as RPopover from '@radix-ui/react-popover'
import { useId, useMemo, useState, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from 'cn'
import { Button } from './Button'
import { Icon } from './Icon'
import { inputClasses } from './Input'
import { Row, Stack } from './layout'
import { Heading, Text } from './text'
import type { Tone } from './tone'

export interface SelectOption {
  value: string
  label: string
}

interface SelectProps {
  value: string
  onChange: (value: string) => void
  options: SelectOption[]
  id?: string
  describedBy?: string
  placeholder?: string
  disabled?: boolean
  label?: string
}

export function Select({ value, onChange, options, id, describedBy, placeholder, disabled, label }: SelectProps) {
  return (
    <RSelect.Root value={value} onValueChange={onChange} disabled={disabled}>
      <RSelect.Trigger
        id={id}
        className={`${inputClasses()} flex flex-row items-center flex-wrap min-w-0 gap-4 justify-between`}
        aria-describedby={describedBy}
        aria-label={label}
      >
        <RSelect.Value placeholder={placeholder} />
        <RSelect.Icon>
          <Icon name="expand" size="sm" />
        </RSelect.Icon>
      </RSelect.Trigger>
      <RSelect.Portal>
        <RSelect.Content className="pouf-popover pouf-popover--select" position="popper" sideOffset={8}>
          <RSelect.Viewport>
            {options.map((o) => (
              <RSelect.Item key={o.value} value={o.value} className="pouf-option">
                <RSelect.ItemText>{o.label}</RSelect.ItemText>
              </RSelect.Item>
            ))}
          </RSelect.Viewport>
        </RSelect.Content>
      </RSelect.Portal>
    </RSelect.Root>
  )
}

interface SwitchProps {
  checked: boolean
  onChange: (checked: boolean) => void
  id?: string
  describedBy?: string
  disabled?: boolean
  label?: string
}

export function Switch({ checked, onChange, id, describedBy, disabled, label }: SwitchProps) {
  return (
    <RSwitch.Root
      id={id}
      className={[
        'pouf-switch group w-15 h-8.5 rounded-pill bg-bg border-none p-0.75 cursor-pointer',
        'cushion-field flex-none [transition:background_160ms_ease]',
        'data-[state=checked]:bg-mint disabled:opacity-50 disabled:cursor-not-allowed',
      ].join(' ')}
      checked={checked}
      onCheckedChange={onChange}
      disabled={disabled}
      aria-describedby={describedBy}
      aria-label={label}
    >
      <RSwitch.Thumb className={[
          'pouf-switch__thumb block w-7 h-7 rounded-[50%] bg-surface',
          '[box-shadow:inset_0_-3px_0_rgba(0,0,0,0.12),inset_0_2px_0_rgba(255,255,255,0.9),0_4px_8px_rgba(58,46,92,0.2)]',
          '[transition:transform_160ms_cubic-bezier(0.2,0.9,0.3,1.3)] transform-[translateX(0)]',
          'group-data-[state=checked]:transform-[translateX(26px)]',
        ].join(' ')} />
    </RSwitch.Root>
  )
}

export function TooltipProvider({ children }: { children: ReactNode }) {
  return <RTooltip.Provider delayDuration={300}>{children}</RTooltip.Provider>
}

// Not asChild: pouf primitives forward no props or ref, and a disabled control gets no pointer events, so this span carries the hover.
export function Tooltip({ tip, children }: { tip: string; children: ReactNode }) {
  return (
    <RTooltip.Root>
      <RTooltip.Trigger asChild>
        <span className="pouf-tip-anchor">{children}</span>
      </RTooltip.Trigger>
      <RTooltip.Portal>
        <RTooltip.Content className="pouf-tooltip" sideOffset={8}>
          {tip}
        </RTooltip.Content>
      </RTooltip.Portal>
    </RTooltip.Root>
  )
}

interface DialogProps {
  trigger: ReactNode
  title: string
  description?: string
  children: ReactNode
  open?: boolean
  onOpenChange?: (open: boolean) => void
  size?: 'md' | 'lg'
}

// Dismisses easily; Confirm (AlertDialog) does not, on purpose. Enter and exit are CSS keyframes off Radix's data-state.
export function Dialog({ trigger, title, description, children, open, onOpenChange, size = 'md' }: DialogProps) {
  return (
    <RDialog.Dialog open={open} onOpenChange={onOpenChange}>
      <RDialog.DialogTrigger asChild>{trigger}</RDialog.DialogTrigger>
      <RDialog.DialogPortal>
        <RDialog.DialogOverlay />
        <RDialog.ContentInner size={size}>
          <Stack gap={4}>
            <div className="pouf-dialog__head">
              <Stack gap={1}>
                <RDialog.DialogTitle>
                    {title}
                </RDialog.DialogTitle>
                {description && (
                  <RDialog.DialogDescription>
                        {description}
                  </RDialog.DialogDescription>
                )}
              </Stack>
              <RDialog.DialogClose asChild>
                <Button variant="quiet" size="sm" label="Close">
                  <Icon name="close" size="sm" />
                </Button>
              </RDialog.DialogClose>
            </div>
            <RDialog.DialogBody>
              {children}
            </RDialog.DialogBody>
          </Stack>
        </RDialog.ContentInner>
      </RDialog.DialogPortal>
    </RDialog.Dialog>
  )
}

interface ConfirmProps {
  children: ReactNode
  title: string
  body: string
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  tone?: Tone
  loading?: boolean
  details?: ReactNode
}

export function Confirm({
  children,
  title,
  body,
  confirmLabel,
  cancelLabel,
  onConfirm,
  tone = 'orange',
  loading,
  details,
}: ConfirmProps) {
  return (
    <RAlert.Root>
      <RAlert.Trigger asChild>{children}</RAlert.Trigger>
      <RAlert.Portal>
        <RAlert.Overlay className="pouf-overlay" />
        <RAlert.Content className="pouf-dialog">
          <Stack gap={4}>
            <RAlert.Title asChild>
              <div>
                <Heading level={3}>{title}</Heading>
              </div>
            </RAlert.Title>
            <RAlert.Description asChild>
              <div>
                <Text muted>{body}</Text>
              </div>
            </RAlert.Description>
            {details}
            <Row gap={3} justify="end">
              <RAlert.Cancel asChild>
                <Button variant="quiet" size="sm">
                  {cancelLabel}
                </Button>
              </RAlert.Cancel>
              <RAlert.Action asChild>
                <Button tone={tone} size="sm" onClick={onConfirm} loading={loading}>
                  {confirmLabel}
                </Button>
              </RAlert.Action>
            </Row>
          </Stack>
        </RAlert.Content>
      </RAlert.Portal>
    </RAlert.Root>
  )
}

interface ComboboxProps {
  value: string
  onChange: (value: string) => void
  options: string[]
  loading?: boolean
  error?: string
  onOpen?: () => void
  id?: string
  describedBy?: string
  placeholder?: string
  mono?: boolean
  label?: string
}

// Hand-rolled keyboard nav: Radix has no combobox. Typing an id the list lacks is the contract, so `error` never disables it.
export function Combobox({
  value,
  onChange,
  options,
  loading,
  error,
  onOpen,
  id,
  describedBy,
  placeholder,
  mono,
  label,
}: ComboboxProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listboxId = useId()
  const optionPrefix = useId()

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q ? options.filter((o) => o.toLowerCase().includes(q)) : options
  }, [options, query])

  const typed = query.trim()
  const canCreate = typed.length > 0 && !options.includes(typed)
  const createIndex = shown.length
  const optionCount = shown.length + (canCreate ? 1 : 0)
  const activeIndex = optionCount > 0 ? Math.min(active, optionCount - 1) : -1
  const activeOptionId =
    activeIndex < 0
      ? undefined
      : activeIndex === createIndex && canCreate
        ? `${optionPrefix}-create`
        : `${optionPrefix}-${activeIndex}`

  const commit = (v: string) => {
    onChange(v)
    setOpen(false)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    const max = optionCount - 1
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      if (max >= 0) setActive((i) => Math.min(i + 1, max))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (canCreate && activeIndex === createIndex) commit(typed)
      else if (shown[activeIndex] !== undefined) commit(shown[activeIndex])
    }
  }

  return (
    <RPopover.Root
      open={open}
      onOpenChange={(o) => {
        setOpen(o)
        if (o) {
          setQuery('')
          setActive(0)
          onOpen?.()
        }
      }}
    >
      <RPopover.Trigger asChild>
        <button
          type="button"
          id={id}
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={open ? listboxId : undefined}
          aria-describedby={describedBy}
          aria-label={label}
          className={cn(
            inputClasses({ mono }),
            'flex flex-row items-center flex-wrap min-w-0 gap-4 justify-between',
          )}
        >
          <span className={cn(!value && 'pouf-combobox__placeholder')}>{value || placeholder}</span>
          <Icon name="expand" size="sm" />
        </button>
      </RPopover.Trigger>
      <RPopover.Portal>
        <RPopover.Content className="pouf-popover pouf-popover--combobox" sideOffset={8} align="start">
          <input
            className={inputClasses({ mono })}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setActive(0)
            }}
            onKeyDown={onKeyDown}
            placeholder="Search, or type a value…"
            role="combobox"
            aria-label={label ? `Search ${label}` : 'Search options'}
            aria-autocomplete="list"
            aria-expanded="true"
            aria-controls={listboxId}
            aria-activedescendant={activeOptionId}
            autoComplete="off"
            spellCheck={false}
            autoFocus
          />
          <div className="pouf-combobox__list" id={listboxId} role="listbox" aria-busy={loading || undefined}>
            {loading && <div className="pouf-combobox__note" role="status" aria-live="polite">Loading options…</div>}
            {!loading && error && <div className="pouf-combobox__note" role="status" aria-live="polite">{error}</div>}
            {!loading &&
              shown.map((o, i) => (
                <button
                  key={o}
                  id={`${optionPrefix}-${i}`}
                  type="button"
                  role="option"
                  tabIndex={-1}
                  aria-selected={o === value}
                  data-highlighted={i === active ? '' : undefined}
                  data-state={o === value ? 'checked' : undefined}
                  className="pouf-option flex-wrap min-w-0 justify-between"
                  onClick={() => commit(o)}
                  onMouseEnter={() => setActive(i)}
                >
                  <span>{o}</span>
                  {o === value && <Icon name="ok" size="sm" />}
                </button>
              ))}
            {canCreate && (
              <button
                type="button"
                id={`${optionPrefix}-create`}
                role="option"
                tabIndex={-1}
                aria-selected={false}
                data-highlighted={active === createIndex ? '' : undefined}
                className="pouf-option"
                onClick={() => commit(typed)}
                onMouseEnter={() => setActive(createIndex)}
              >
                Use “{typed}”
              </button>
            )}
            {!loading && !error && shown.length === 0 && !canCreate && (
              <div className="pouf-combobox__note">No models offered. Type an id.</div>
            )}
          </div>
        </RPopover.Content>
      </RPopover.Portal>
    </RPopover.Root>
  )
}
