import type { Metadata } from "next"
import { Nunito } from "next/font/google"
import { notFound } from "next/navigation"
import { hasLocale, NextIntlClientProvider } from "next-intl"
import { getTranslations } from "next-intl/server"
import { fetchCountries } from "@/api/countries"
import { CountryProvider } from "@/components/country/country-provider"
import { MobileNav } from "@/components/layout/mobile-nav"
import { SiteAnalytics } from "@/components/layout/site-analytics"
import { SiteFooter } from "@/components/layout/site-footer"
import { SiteHeader } from "@/components/layout/site-header"
import { Toaster } from "@/components/pouf/toaster"
import { TourProvider } from "@/components/tour/tour-context"
import { routing } from "@/i18n/routing"
import { clientEnv } from "@/lib/env/client"
import { INDEXABLE } from "@/lib/seo"
import "../globals.css"

const body = Nunito({
  variable: "--font-body",
  subsets: ["latin", "latin-ext", "cyrillic"],
  display: "swap",
})

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }))
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>
}): Promise<Metadata> {
  const { locale } = await params
  const t = await getTranslations({ locale, namespace: "app" })

  return {
    metadataBase: new URL(clientEnv.NEXT_PUBLIC_SITE_URL),
    title: {
      default: t("name"),
      template: t("titleTemplate", { page: "%s" }),
    },
    description: t("description"),
    robots: INDEXABLE ? undefined : { index: false, follow: false },
    openGraph: {
      type: "website",
      siteName: t("name"),
      locale,
      title: t("name"),
      description: t("description"),
    },
    twitter: { card: "summary_large_image" },
  }
}

export default async function LocaleLayout({
  children,
  modal,
  params,
}: {
  children: React.ReactNode
  modal: React.ReactNode
  params: Promise<{ locale: string }>
}) {
  const { locale } = await params
  if (!hasLocale(routing.locales, locale)) {
    notFound()
  }

  const countries = await fetchCountries()

  return (
    <html lang={locale} className={`${body.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <NextIntlClientProvider>
          <CountryProvider countries={countries}>
            <TourProvider>
              <div className="container mx-auto flex w-full flex-1 flex-col p-4 max-md:pb-[calc(96px+env(safe-area-inset-bottom))] md:p-8 lg:px-16 xl:px-32">
                <SiteHeader />
                <main className="flex-1 pt-8">{children}</main>
                {modal}
                <SiteFooter />
              </div>
              <MobileNav />

              <div className="pouf-toasts">
                <Toaster />
              </div>
            </TourProvider>
          </CountryProvider>
        </NextIntlClientProvider>
        {process.env.VERCEL ? <SiteAnalytics /> : null}
      </body>
    </html>
  )
}
