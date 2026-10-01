"use client"

import { useEffect } from 'react'
import { cn } from 'cn'
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion'
import type { ReactNode } from 'react'
import { Icon } from './Icon'

const AUTO_DISMISS_MS = 6_000

export type ToastSeverity = 'critical' | 'info'

function emphasise(text: string): ReactNode[] {
  return text.split(/\*([^*]+)\*/g).map((part, i) =>
    i % 2 === 1 ? <strong key={i}>{part}</strong> : <span key={i}>{part}</span>,
  )
}

interface ToastProps {
  id: number
  severity: ToastSeverity
  children: ReactNode
  /** Keep it referentially stable, or the auto-dismiss timer keeps resetting. */
  onDismiss: (id: number) => void
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

export function Toast({ id, severity, children, onDismiss }: ToastProps) {
  const critical = severity === 'critical'
  const reduce = useReducedMotion() ?? false

  // Every dep must be stable: a re-run resets the timer, and a 6s toast under a 5s poll never fades.
  useEffect(() => {
    if (critical) return // critical never fades
    const t = setTimeout(() => onDismiss(id), AUTO_DISMISS_MS)
    return () => clearTimeout(t)
  }, [critical, id, onDismiss])

  return (
    <motion.div
      className={cn('pouf-toast', critical ? 'tone-down' : 'tone-info')}
      role={critical ? 'alert' : 'status'}
      aria-live={critical ? 'assertive' : 'polite'}
      layout="position"
      {...toastMotion(reduce)}
    >
      <div className="pouf-toast__icon">
        <Icon name={critical ? 'warn' : 'ok'} size="sm" />
      </div>
      <div className="pouf-toast__body">{children}</div>
      <button type="button" className="pouf-toast__close" onClick={() => onDismiss(id)} aria-label="Dismiss notification">
        <Icon name="close" size="sm" />
      </button>
    </motion.div>
  )
}

export interface ToastItem {
  id: number
  severity: ToastSeverity
  text: string
}

export function ToastViewport({ toasts, onDismiss }: { toasts: ToastItem[]; onDismiss: (id: number) => void }) {
  if (toasts.length === 0) return null
  return (
    <AnimatePresence mode="sync">
      {toasts.map((t) => (
        <Toast key={t.id} id={t.id} severity={t.severity} onDismiss={onDismiss}>
          {emphasise(t.text)}
        </Toast>
      ))}
    </AnimatePresence>
  )
}
