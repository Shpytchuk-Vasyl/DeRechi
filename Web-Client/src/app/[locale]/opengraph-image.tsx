import { ImageResponse } from "next/og"
import { hasLocale } from "next-intl"
import { getTranslations } from "next-intl/server"
import { routing } from "@/i18n/routing"
import { nunito } from "@/lib/og-font"

export const alt = "DeRechi"
export const size = { width: 1200, height: 630 }
export const contentType = "image/png"

export default async function Image({ params }: { params: Promise<{ locale: string }> }) {
  const { locale: requested } = await params
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale
  const t = await getTranslations({ locale, namespace: "app" })

  const name = t("name")
  const description = t("description")
  const font = await nunito(name + description)

  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        padding: "80px 96px",
        background: "linear-gradient(135deg, #fff7ed 0%, #ffe4e6 100%)",
        color: "#1c1917",
        fontFamily: "Nunito",
      }}
    >
      <div style={{ fontSize: 112, fontWeight: 800, letterSpacing: -4 }}>{name}</div>
      <div style={{ marginTop: 24, fontSize: 44, lineHeight: 1.25, maxWidth: 960, opacity: 0.8 }}>
        {description}
      </div>
    </div>,
    {
      ...size,
      fonts: font ? [{ name: "Nunito", data: font, style: "normal", weight: 800 }] : undefined,
    },
  )
}
