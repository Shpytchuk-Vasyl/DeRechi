export type Arrival = {
  landed: Promise<boolean>
  cancel: () => void
}

const MIN_FLIGHT_MS = 1200
const MAX_FLIGHT_MS = 1800
const FULL_FLIGHT_SCREENS = 3
const START_DELAY_MS = 10
const PRESS_MS = 140
const RELEASE_MS = 240
const PRESS_DEPTH = "2px"
const POP = "cubic-bezier(0.2, 0.9, 0.3, 1.3)"
const INTERRUPTS = ["wheel", "touchstart", "keydown", "pointerdown"] as const

let pending: string | null = null

export function sectionOf(href: string) {
  return href.split("#")[1] ?? ""
}

export function requestArrival(section: string) {
  pending = section
}

export function pendingArrival() {
  return pending !== null && window.location.hash === `#${pending}` ? pending : null
}

export function clearArrival() {
  pending = null
}

export function arrive(target: HTMLElement): Arrival {
  const interrupted = new AbortController()
  const margin = Number.parseFloat(getComputedStyle(target).scrollMarginTop) || 0
  const destination = () => Math.min(documentTop(target) - margin, maxScroll())

  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    window.scrollTo({ top: destination(), behavior: "instant" })
    return { landed: Promise.resolve(true), cancel: () => {} }
  }

  const landed = (async () => {
    const listeners = new AbortController()
    for (const type of INTERRUPTS) {
      window.addEventListener(type, () => interrupted.abort(), {
        passive: true,
        signal: listeners.signal,
      })
    }
    try {
      await wait(START_DELAY_MS, interrupted.signal)
      const from = window.scrollY
      await glide(from, destination, flightDuration(destination() - from), interrupted.signal)
      if (interrupted.signal.aborted) return false
      await press(target)
      return true
    } finally {
      listeners.abort()
    }
  })()

  return { landed, cancel: () => interrupted.abort() }
}

function flightDuration(distance: number) {
  const share = Math.min(1, Math.abs(distance) / (window.innerHeight * FULL_FLIGHT_SCREENS))
  return MIN_FLIGHT_MS + (MAX_FLIGHT_MS - MIN_FLIGHT_MS) * share
}

function glide(from: number, to: () => number, duration: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    if (signal.aborted) {
      resolve()
      return
    }
    const start = performance.now()
    const frame = (now: number) => {
      if (signal.aborted) {
        resolve()
        return
      }
      const t = Math.min(1, (now - start) / duration)
      window.scrollTo({ top: from + (to() - from) * easeOutQuint(t), behavior: "instant" })
      if (t < 1) requestAnimationFrame(frame)
      else resolve()
    }
    requestAnimationFrame(frame)
  })
}

function press(card: HTMLElement) {
  const rest = getComputedStyle(card).boxShadow
  const pressed = rest.replace(/(-?\d*\.?\d+)px/g, (_, value) => `${Number(value) / 2}px`)
  return card
    .animate(
      [
        { translate: "0 0", boxShadow: rest, easing: "ease" },
        {
          translate: `0 ${PRESS_DEPTH}`,
          boxShadow: pressed,
          offset: PRESS_MS / (PRESS_MS + RELEASE_MS),
          easing: POP,
        },
        { translate: "0 0", boxShadow: rest },
      ],
      PRESS_MS + RELEASE_MS,
    )
    .finished.then(() => undefined)
}

function wait(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(timer)
        resolve()
      },
      { once: true },
    )
  })
}

function easeOutQuint(t: number) {
  return 1 - (1 - t) ** 5
}

function documentTop(element: HTMLElement) {
  let top = 0
  for (let node: Element | null = element; node instanceof HTMLElement; node = node.offsetParent) {
    top += node.offsetTop
  }
  return top
}

function maxScroll() {
  return Math.max(0, document.documentElement.scrollHeight - window.innerHeight)
}
