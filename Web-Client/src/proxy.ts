import { type NextRequest, NextResponse } from "next/server"
import { hasLocale } from "next-intl"
import createIntlMiddleware from "next-intl/middleware"
import { paths } from "@/i18n/paths"
import { routing } from "@/i18n/routing"
import {
  COUNTRY_COOKIE,
  COUNTRY_COOKIE_MAX_AGE,
  GEO_COUNTRY_HEADER,
  normaliseCountryCode,
} from "@/lib/intl/country"

const intl = createIntlMiddleware(routing)

const OPEN_DURING_MAINTENANCE = new Set<string>([
  paths.home,
  paths.terms,
  paths.privacy,
  paths.maintenance,
])

function maintenanceRedirect(request: NextRequest): NextResponse | null {
  if (!process.env.NEXT_PUBLIC_MAINTENANCE) return null

  const [, locale, ...rest] = request.nextUrl.pathname.split("/")
  if (!hasLocale(routing.locales, locale)) return null

  const path = `/${rest.join("/")}`.replace(/\/+$/, "") || paths.home
  if (OPEN_DURING_MAINTENANCE.has(path)) return null

  return NextResponse.redirect(new URL(`/${locale}${paths.maintenance}`, request.url))
}

export default function proxy(request: NextRequest) {
  const response = maintenanceRedirect(request) ?? intl(request)

  if (!request.cookies.has(COUNTRY_COOKIE)) {
    const geo = normaliseCountryCode(request.headers.get(GEO_COUNTRY_HEADER))
    if (geo) {
      response.cookies.set(COUNTRY_COOKIE, geo, {
        path: "/",
        maxAge: COUNTRY_COOKIE_MAX_AGE,
        sameSite: "lax",
      })
    }
  }

  return response
}

export const config = {
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
}
