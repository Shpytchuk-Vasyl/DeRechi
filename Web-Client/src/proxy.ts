import type { NextRequest } from "next/server"
import createIntlMiddleware from "next-intl/middleware"
import { routing } from "@/i18n/routing"
import {
  COUNTRY_COOKIE,
  COUNTRY_COOKIE_MAX_AGE,
  GEO_COUNTRY_HEADER,
  normaliseCountryCode,
} from "@/lib/intl/country"

const intl = createIntlMiddleware(routing)

export default function proxy(request: NextRequest) {
  const response = intl(request)

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
