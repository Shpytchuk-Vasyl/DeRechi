import * as RAccordion from '@radix-ui/react-accordion'
import * as RTabs from '@radix-ui/react-tabs'
import * as RCollapse from '@radix-ui/react-collapsible'
import { cn } from 'cn'
import { buttonClasses } from './Button'
import { useState, type ReactNode } from 'react'
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion'
import { Icon } from './Icon'
import { toneClass, type Tone } from './tone'

interface Tab {
  value: string
  label: string
  content: ReactNode
}

interface TabsProps {
  tabs: Tab[]
  value: string
  onChange: (value: string) => void
  tone?: Tone
}

export function Tabs({ tabs, value, onChange, tone = 'blue' }: TabsProps) {
  const reduceMotion = useReducedMotion()
  return (
    <RTabs.Root className="pouf-tabs" value={value} onValueChange={onChange}>
      <RTabs.List className="pouf-tabs__list">
        {tabs.map((t) => (
          <RTabs.Trigger
            key={t.value}
            value={t.value}
            className={cn(buttonClasses({ size: 'sm', tone }), 'pouf-tabs__trigger')}
          >
            {t.label}
          </RTabs.Trigger>
        ))}
      </RTabs.List>
      <AnimatePresence mode="wait">
        {tabs.map(
          (t) =>
            t.value === value && (
              <RTabs.Content
                key={t.value}
                value={t.value}
                className="pouf-tabs__content"
                forceMount
                asChild
              >
                <motion.div
                  initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -6 }}
                  transition={{ duration: reduceMotion ? 0.01 : 0.18, ease: 'easeOut' }}
                >
                  {t.content}
                </motion.div>
              </RTabs.Content>
            ),
        )}
      </AnimatePresence>
    </RTabs.Root>
  )
}

interface AccordionItem {
  value: string
  title: string
  children: ReactNode
}

interface AccordionProps {
  items: AccordionItem[]
  defaultValue?: string
}

// CSS keyframes, no framer: the motion build never told a closed panel to close.
export function Accordion({ items, defaultValue }: AccordionProps) {
  return (
    <RAccordion.Root
      className="pouf-accordion"
      type="single"
      defaultValue={defaultValue as string}
      collapsible
    >
      {items.map((item) => (
        <RAccordion.Item key={item.value} value={item.value} className="pouf-accordion__item">
          <RAccordion.Header>
            <RAccordion.Trigger className="pouf-accordion__trigger">
              <span className="pouf-accordion__title">{item.title}</span>
              <span className="pouf-accordion__chevron">
                <Icon name="expand" size="sm" />
              </span>
            </RAccordion.Trigger>
          </RAccordion.Header>
          <RAccordion.Content className="pouf-accordion__content">
            <div className="pouf-accordion__body">{item.children}</div>
          </RAccordion.Content>
        </RAccordion.Item>
      ))}
    </RAccordion.Root>
  )
}

interface CollapsibleProps {
  children: ReactNode
  trigger: ReactNode
  open?: boolean
  onOpenChange?: (open: boolean) => void
}

export function Collapsible({ children, trigger, open: controlledOpen, onOpenChange: controlledOnOpen }: CollapsibleProps) {
  const reduceMotion = useReducedMotion()
  const [internalOpen, setInternalOpen] = useState(false)
  const isControlled = controlledOpen !== undefined
  const open = isControlled ? controlledOpen : internalOpen
  const setOpen = isControlled ? controlledOnOpen : setInternalOpen

  return (
    <RCollapse.Root open={open} onOpenChange={setOpen}>
      <RCollapse.Trigger asChild>
        <button type="button" className="pouf-collapsible__trigger">
          {trigger}
        </button>
      </RCollapse.Trigger>
      <RCollapse.Content className="pouf-collapsible__content" forceMount>
        <motion.div
          initial={false}
          animate={{ height: open ? 'auto' : 0, opacity: open ? 1 : 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.2, ease: [0.2, 0.9, 0.3, 1] }}
          style={{ overflow: 'hidden' }}
        >
          <div className="pouf-collapsible__body">{children}</div>
        </motion.div>
      </RCollapse.Content>
    </RCollapse.Root>
  )
}
