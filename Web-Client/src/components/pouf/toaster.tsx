"use client"

import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion'
import { useTranslations } from 'next-intl'
import { Icon } from './Icon'
import type { IconName } from './Icon'

export type ToastVariant = 'default' | 'success' | 'error' | 'warning' | 'info'

interface ToastAction {
  label: string
  onClick: () => void
}

interface ToastOptions {
  description?: string
  duration?: number
  action?: ToastAction
}

interface ToastEntry {
  id: string
  title: string
  description?: string
  variant: ToastVariant
  duration: number
  action?: ToastAction
}

let count = 0
let toasts: ToastEntry[] = []
let listeners: Array<() => void> = []

function emit() {
  for (const fn of listeners) fn()
}

function add(entry: ToastEntry) {
  toasts = [...toasts, entry]
  emit()
}

function remove(id: string) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

const ICON: Record<ToastVariant, IconName> = {
  default: 'ok',
  success: 'ok',
  error: 'fail',
  warning: 'warn',
  info: 'info',
}

const DEFAULT_DURATION = 5_000

function createToast(variant: ToastVariant) {
  return (title: string, opts?: ToastOptions) => {
    const id = `toast-${++count}`
    const entry: ToastEntry = {
      id,
      title,
      description: opts?.description,
      variant,
      duration: opts?.duration ?? DEFAULT_DURATION,
      action: opts?.action,
    }
    add(entry)
    return id
  }
}

export const toast = Object.assign(createToast('default'), {
  success: createToast('success'),
  error: createToast('error'),
  warning: createToast('warning'),
  info: createToast('info'),
})

export function dismissToast(id: string) {
  remove(id)
}

function toastMotion(reduce: boolean) {
  if (reduce) {
    return {
      initial: { opacity: 0 },
      animate: { opacity: 1 },
      exit: { opacity: 0 },
      transition: { duration: 0.12 },
    }
  }
  return {
    initial: { opacity: 0, x: 140, scale: 0.7 },
    animate: { opacity: 1, x: 0, scale: 1 },
    exit: { opacity: 0, x: 100, scale: 0.8, transition: { duration: 0.14, ease: 'easeIn' as const } },
    transition: { type: 'spring' as const, stiffness: 280, damping: 26, mass: 1 },
  }
}

function ToastItem({ t, onDismiss }: { t: ToastEntry; onDismiss: (id: string) => void }) {
  const sticky = t.duration === Infinity
  const reduce = useReducedMotion() ?? false
  const tc = useTranslations('common')

  useEffect(() => {
    if (sticky) return
    const timer = setTimeout(() => onDismiss(t.id), t.duration)
    return () => clearTimeout(timer)
  }, [sticky, t.duration, t.id, onDismiss])

  return (
    <motion.div
      className={`pouf-toast pouf-toast--${t.variant}`}
      role={t.variant === 'error' ? 'alert' : 'status'}
      aria-live={t.variant === 'error' ? 'assertive' : 'polite'}
      layout="position"
      {...toastMotion(reduce)}
    >
      <div className="pouf-toast__icon">
        <Icon name={ICON[t.variant]} size="sm" />
      </div>
      <div className="pouf-toast__body">
        <div className="pouf-toast__title">{t.title}</div>
        {t.description && <div className="pouf-toast__desc">{t.description}</div>}
        {t.action && (
          <button type="button" className="pouf-toast__action" onClick={t.action.onClick}>
            {t.action.label}
          </button>
        )}
      </div>
      <button type="button" className="pouf-toast__close" onClick={() => onDismiss(t.id)} aria-label={tc('dismissNotification')}>
        <Icon name="close" size="sm" />
      </button>
    </motion.div>
  )
}

export function Toaster() {
  const [items, setItems] = useState<ToastEntry[]>(toasts)

  useEffect(() => {
    const listener = () => setItems([...toasts])
    listeners.push(listener)
    return () => {
      listeners = listeners.filter((l) => l !== listener)
    }
  }, [])

  const dismiss = useCallback((id: string) => remove(id), [])

  return (
    <AnimatePresence mode="popLayout">
      {items.map((t) => (
        <ToastItem key={t.id} t={t} onDismiss={dismiss} />
      ))}
    </AnimatePresence>
  )
}
