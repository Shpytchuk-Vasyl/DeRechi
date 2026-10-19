import { ImageResponse } from "next/og"
import { getFormatter, getTranslations } from "next-intl/server"
import { fetchItem, type ItemKind } from "@/api/items"
import { paths } from "@/i18n/paths"
import { locales } from "@/i18n/routing"
import { cityOf } from "@/lib/cities"
import { absoluteFileUrl } from "@/lib/env/client"
import { formatMoney } from "@/lib/intl/money"
import { type NunitoWeight, nunito } from "@/lib/og-font"
import { qrDataUrl } from "@/lib/qr"
import { absoluteUrl, SITE_URL } from "@/lib/seo"
import { noticePlace } from "@/screens/found_lost/notice-place"

const WIDTH = 1200
const HEIGHT = 630
const CARD = 1080
const CARD_PADDING = 56
const CARD_PHOTO_HEIGHT = 440
const CARD_QR = 220
const TITLE_MAX = 110
const PHOTO_TYPES = /^image\/(jpeg|png)$/

const COLORS = {
  bg: "#f0e9ff",
  ink: "#3a2e5c",
  muted: "#71609b",
  surface: "#ffffff",
  lost: "#ffb3d1",
  found: "#a8f0d0",
  reward: "#ffe58a",
}

type Font = { name: string; data: ArrayBuffer; weight: NunitoWeight; style: "normal" }

type Frame = {
  brand: string
  host: string
  logo: string | null
}

type Notice = {
  kind: ItemKind
  label: string
  title: string
  details: string[]
  reward: string | null
  photo: string | null
}

export async function itemOgImage(
  kind: ItemKind,
  locale: string,
  id: string,
): Promise<ImageResponse> {
  const [notice, app] = await Promise.all([
    loadNotice(kind, locale, id),
    getTranslations({ locale, namespace: "app" }),
  ])
  const title = notice?.title ?? app("homeTitle")
  const details = notice?.details ?? [app("description")]
  const frame = await loadFrame(locale)
  const fonts = await loadFonts(
    [...details, frame.host].join(""),
    [title, frame.brand, notice?.label ?? "", notice?.reward ?? ""].join(""),
  )

  return new ImageResponse(
    <OgCard notice={notice} title={title} details={details} frame={frame} />,
    { width: WIDTH, height: HEIGHT, fonts },
  )
}

export async function itemCardImage(kind: ItemKind, locale: string, id: string): Promise<Response> {
  if (!(locales as readonly string[]).includes(locale)) return new Response(null, { status: 404 })

  const [notice, t] = await Promise.all([
    loadNotice(kind, locale, id),
    getTranslations({ locale, namespace: "item" }),
  ])
  if (!notice) return new Response(null, { status: 404 })

  const frame = await loadFrame(locale)
  const url = absoluteUrl(locale, paths.item(kind, id))
  const link = url.replace(/^https?:\/\/(www\.)?/, "")
  const scan = t("cardScan")
  const fonts = await loadFonts(
    [...notice.details, scan].join(""),
    [notice.title, notice.label, notice.reward ?? "", frame.brand, link].join(""),
  )

  return new ImageResponse(
    <NoticeCard
      notice={notice}
      frame={frame}
      qr={qrDataUrl(url, COLORS.ink)}
      scan={scan}
      link={link}
    />,
    {
      width: CARD,
      height: CARD,
      fonts,
      headers: { "cache-control": "public, max-age=3600", "x-robots-tag": "noindex" },
    },
  )
}

async function loadNotice(kind: ItemKind, locale: string, id: string): Promise<Notice | null> {
  const item = await fetchItem(kind, id)
  if (!item) return null

  const [t, tcity, format] = await Promise.all([
    getTranslations({ locale, namespace: "item" }),
    getTranslations({ locale, namespace: "city" }),
    getFormatter({ locale }),
  ])
  const city = cityOf(item.place)
  const reward = item.compensation
    ? item.compensation.amount > 0
      ? t("reward", { amount: formatMoney(format, item.compensation) })
      : t("rewardNegotiable")
    : null

  return {
    kind,
    label: t(kind).toLocaleUpperCase(locale),
    title: clip(item.title, TITLE_MAX),
    details: [
      noticePlace(item.place.name, city ? tcity(`${city.slug}.name`) : undefined),
      format.dateTime(new Date(item.date), { dateStyle: "long" }),
    ],
    reward,
    photo: item.image ? await imageDataUrl(absoluteFileUrl(item.image)) : null,
  }
}

async function loadFrame(locale: string): Promise<Frame> {
  const [app, logo] = await Promise.all([
    getTranslations({ locale, namespace: "app" }),
    imageDataUrl(`${SITE_URL}/apple-icon.png`),
  ])
  return { brand: app("name"), host: new URL(SITE_URL).host.replace(/^www\./, ""), logo }
}

async function loadFonts(regularText: string, boldText: string): Promise<Font[] | undefined> {
  const [regular, bold] = await Promise.all([nunito(regularText, 600), nunito(boldText, 800)])
  const fonts = [regular && font(regular, 600), bold && font(bold, 800)].filter(
    (it): it is Font => it !== null,
  )
  return fonts.length > 0 ? fonts : undefined
}

function OgCard({
  notice,
  title,
  details,
  frame,
}: {
  notice: Notice | null
  title: string
  details: string[]
  frame: Frame
}) {
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
      {notice?.photo ? (
        // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
        <img
          src={notice.photo}
          alt=""
          width={HEIGHT}
          height={HEIGHT}
          style={{ objectFit: "cover" }}
        />
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
          {notice ? <Plaques notice={notice} size={30} /> : null}
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
          <Details lines={details} size={32} />
        </div>

        <Brand frame={frame} withHost />
      </div>
    </div>
  )
}

function NoticeCard({
  notice,
  frame,
  qr,
  scan,
  link,
}: {
  notice: Notice
  frame: Frame
  qr: string
  scan: string
  link: string
}) {
  const inner = CARD - CARD_PADDING * 2

  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: CARD_PADDING,
        background: COLORS.bg,
        fontFamily: "Nunito",
        color: COLORS.ink,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <Plaques notice={notice} size={40} />
        <Brand frame={frame} withName={!notice.reward} />
      </div>

      {notice.photo ? (
        // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
        <img
          src={notice.photo}
          alt=""
          width={inner}
          height={CARD_PHOTO_HEIGHT}
          style={{ objectFit: "cover", borderRadius: 36 }}
        />
      ) : (
        <div
          style={{
            width: inner,
            height: CARD_PHOTO_HEIGHT,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: 36,
            background: COLORS[notice.kind],
          }}
        >
          {frame.logo ? (
            // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
            <img src={frame.logo} alt="" width={180} height={180} style={{ borderRadius: 44 }} />
          ) : null}
        </div>
      )}

      <div style={{ display: "flex", gap: 40, alignItems: "flex-end" }}>
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 18 }}>
          <div
            style={{
              display: "flex",
              fontSize: titleSize(notice.title) - 8,
              fontWeight: 800,
              lineHeight: 1.15,
            }}
          >
            {notice.title}
          </div>
          <Details lines={notice.details} size={30} />
          <div style={{ display: "flex", fontSize: 28, fontWeight: 800 }}>{link}</div>
        </div>

        <div
          style={{
            width: CARD_QR + 40,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 12,
          }}
        >
          <div
            style={{ display: "flex", padding: 20, borderRadius: 28, background: COLORS.surface }}
          >
            {/* biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image */}
            <img src={qr} alt="" width={CARD_QR} height={CARD_QR} />
          </div>
          <div
            style={{
              display: "flex",
              textAlign: "center",
              fontSize: 22,
              fontWeight: 600,
              lineHeight: 1.25,
              color: COLORS.muted,
            }}
          >
            {scan}
          </div>
        </div>
      </div>
    </div>
  )
}

function Plaques({ notice, size }: { notice: Notice; size: number }) {
  const pill = {
    display: "flex",
    padding: `${Math.round(size * 0.35)}px ${Math.round(size * 0.8)}px`,
    borderRadius: 999,
    fontSize: size,
    fontWeight: 800,
  }

  return (
    <div style={{ display: "flex", gap: 14 }}>
      <div style={{ ...pill, background: COLORS[notice.kind], letterSpacing: size * 0.06 }}>
        {notice.label}
      </div>
      {notice.reward ? (
        <div style={{ ...pill, background: COLORS.reward }}>{notice.reward}</div>
      ) : null}
    </div>
  )
}

function Details({ lines, size }: { lines: string[]; size: number }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 6,
        fontSize: size,
        fontWeight: 600,
        color: COLORS.muted,
      }}
    >
      {lines.map((line) => (
        <div key={line} style={{ display: "flex" }}>
          {line}
        </div>
      ))}
    </div>
  )
}

function Brand({
  frame,
  withName = true,
  withHost = false,
}: {
  frame: Frame
  withName?: boolean
  withHost?: boolean
}) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
      {frame.logo ? (
        // biome-ignore lint/performance/noImgElement: next/og renders plain elements, not next/image
        <img src={frame.logo} alt="" width={64} height={64} style={{ borderRadius: 16 }} />
      ) : null}
      {withName ? (
        <div style={{ display: "flex", fontSize: 36, fontWeight: 800 }}>{frame.brand}</div>
      ) : null}
      {withHost ? (
        <div style={{ display: "flex", fontSize: 28, fontWeight: 600, color: COLORS.muted }}>
          {frame.host}
        </div>
      ) : null}
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
