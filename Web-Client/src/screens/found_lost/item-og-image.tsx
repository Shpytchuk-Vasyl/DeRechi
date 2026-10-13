import { ImageResponse } from "next/og"
import { getFormatter, getTranslations } from "next-intl/server"
import { fetchItem, type ItemKind } from "@/api/items"
import { absoluteFileUrl } from "@/lib/env/client"
import { type NunitoWeight, nunito } from "@/lib/og-font"
import { SITE_URL } from "@/lib/seo"

const WIDTH = 1200
const HEIGHT = 630
const TITLE_MAX = 110
const PHOTO_TYPES = /^image\/(jpeg|png)$/

const COLORS = {
  bg: "#f0e9ff",
  ink: "#3a2e5c",
  muted: "#71609b",
}

type Font = { name: string; data: ArrayBuffer; weight: NunitoWeight; style: "normal" }

type Card = {
  title: string
  details: string[]
  brand: string
  host: string
  photo: string | null
  logo: string | null
}

export async function itemOgImage(
  kind: ItemKind,
  locale: string,
  id: string,
): Promise<ImageResponse> {
  const [item, app, format] = await Promise.all([
    fetchItem(kind, id),
    getTranslations({ locale, namespace: "app" }),
    getFormatter({ locale }),
  ])

  const title = item ? clip(item.title, TITLE_MAX) : app("homeTitle")
  const details = item
    ? [item.place.name, format.dateTime(new Date(item.date), { dateStyle: "long" })]
    : [app("description")]
  const brand = app("name")
  const host = new URL(SITE_URL).host.replace(/^www\./, "")

  const [photo, logo, regular, bold] = await Promise.all([
    item?.image ? imageDataUrl(absoluteFileUrl(item.image)) : null,
    imageDataUrl(`${SITE_URL}/apple-icon.png`),
    nunito(details.join("") + host, 600),
    nunito(title + brand, 800),
  ])

  const fonts = [regular && font(regular, 600), bold && font(bold, 800)].filter(
    (it): it is Font => it !== null,
  )

  return new ImageResponse(
    <OgCard title={title} details={details} brand={brand} host={host} photo={photo} logo={logo} />,
    { width: WIDTH, height: HEIGHT, fonts: fonts.length > 0 ? fonts : undefined },
  )
}

function OgCard({ title, details, brand, host, photo, logo }: Card) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        background: COLORS.bg,
        fontFamily: "Nunito",
        color: COLORS.ink,
      }}
    >
      {photo ? (
        // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
        <img src={photo} alt="" width={HEIGHT} height={HEIGHT} style={{ objectFit: "cover" }} />
      ) : null}

      <div
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: 64,
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 28 }}>
          <div
            style={{
              display: "flex",
              fontSize: titleSize(title),
              fontWeight: 800,
              lineHeight: 1.15,
            }}
          >
            {title}
          </div>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 6,
              fontSize: 32,
              fontWeight: 600,
              color: COLORS.muted,
            }}
          >
            {details.map((line) => (
              <div key={line} style={{ display: "flex" }}>
                {line}
              </div>
            ))}
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
          {logo ? (
            // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
            <img src={logo} alt="" width={64} height={64} style={{ borderRadius: 16 }} />
          ) : null}
          <div style={{ display: "flex", fontSize: 36, fontWeight: 800 }}>{brand}</div>
          <div style={{ display: "flex", fontSize: 28, fontWeight: 600, color: COLORS.muted }}>
            {host}
          </div>
        </div>
      </div>
    </div>
  )
}

function titleSize(title: string): number {
  if (title.length <= 40) return 64
  if (title.length <= 70) return 54
  return 44
}

function clip(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`
}

function font(data: ArrayBuffer, weight: Font["weight"]): Font {
  return { name: "Nunito", data, weight, style: "normal" }
}

async function imageDataUrl(url: string): Promise<string | null> {
  try {
    const response = await fetch(url)
    const type = response.headers.get("content-type")?.split(";")[0].trim() ?? ""
    if (!response.ok || !PHOTO_TYPES.test(type)) return null
    return `data:${type};base64,${Buffer.from(await response.arrayBuffer()).toString("base64")}`
  } catch {
    return null
  }
}
