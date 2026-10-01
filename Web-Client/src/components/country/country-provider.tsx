"use client"

import { createContext, type ReactNode, useContext, useSyncExternalStore } from "react"
import {
  COUNTRY_COOKIE,
  COUNTRY_COOKIE_MAX_AGE,
  type Country,
  pickCountry,
  readCountryCookie,
} from "@/lib/country"

type CountryContext = {
  code: string
  currency: string
  countries: Country[]
  setCountry: (code: string) => void
}

const Context = createContext<CountryContext | null>(null)

const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function readCookie(): string | null {
  try {
    return readCountryCookie(document.cookie)
  } catch {
    return null
  }
}

function writeCookie(code: string) {
  try {
    // biome-ignore lint/suspicious/noDocumentCookie: the Cookie Store API is async and still missing in Firefox
    document.cookie = `${COUNTRY_COOKIE}=${code}; path=/; max-age=${COUNTRY_COOKIE_MAX_AGE}; samesite=lax`
  } catch {}
  for (const listener of listeners) listener()
}

export function CountryProvider({
  countries,
  children,
}: {
  countries: Country[]
  children: ReactNode
}) {
  const fromCookie = useSyncExternalStore(subscribe, readCookie, () => null)
  const country = pickCountry(countries, fromCookie)

  return (
    <Context.Provider
      value={{ code: country.code, currency: country.currency, countries, setCountry: writeCookie }}
    >
      {children}
    </Context.Provider>
  )
}

export function useCountry(): CountryContext {
  const value = useContext(Context)
  if (!value) throw new Error("useCountry needs a CountryProvider above it")
  return value
}
