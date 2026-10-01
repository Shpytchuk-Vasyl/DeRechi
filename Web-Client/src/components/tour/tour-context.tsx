"use client"

import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react"

type TourContextValue = {
  register: (run: () => void) => () => void
  start: () => void
  available: boolean
}

const TourContext = createContext<TourContextValue | null>(null)

export function TourProvider({ children }: { children: React.ReactNode }) {
  const run = useRef<(() => void) | null>(null)
  const [available, setAvailable] = useState(false)

  const register = useCallback((next: () => void) => {
    run.current = next
    setAvailable(true)

    return () => {
      run.current = null
      setAvailable(false)
    }
  }, [])

  const start = useCallback(() => {
    run.current?.()
  }, [])

  const value = useMemo(() => ({ register, start, available }), [register, start, available])

  return <TourContext.Provider value={value}>{children}</TourContext.Provider>
}

export function useTour(): TourContextValue | null {
  return useContext(TourContext)
}
