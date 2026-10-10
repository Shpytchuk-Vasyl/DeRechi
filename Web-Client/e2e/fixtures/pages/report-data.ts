import { randomInt } from "node:crypto"
import type { Page } from "@playwright/test"
import {
  type CategoryKey,
  CITIES,
  type City,
  type DataFactory,
  ITEM_NAMES,
  type Locale,
} from "../../support/data"
import { env } from "../../support/env"

export function noticeText(
  data: DataFactory,
  locale: Locale,
  category: CategoryKey = "WALLET",
): { title: string; category: CategoryKey; description: string } {
  return {
    title: data.title(ITEM_NAMES[locale][category]),
    category,
    description: `${ITEM_NAMES[locale].OTHER}, ${ITEM_NAMES[locale].KEYS}.`,
  }
}

export function nationalUaPhone(international: string): string {
  if (!international.startsWith("+380")) throw new Error(`not a UA number: ${international}`)
  const national = `0${international.slice(4)}`
  return `${national.slice(0, 3)} ${national.slice(3, 6)} ${national.slice(6, 8)} ${national.slice(8)}`
}

export function nearby(city: City): { latitude: number; longitude: number } {
  const base = CITIES[city]
  return {
    latitude: base.lat + randomInt(1, 10_000) / 1_000_000,
    longitude: base.lon + randomInt(1, 10_000) / 1_000_000,
  }
}

export async function locateAt(page: Page, city: City): Promise<void> {
  const context = page.context()
  await context.grantPermissions(["geolocation"], { origin: new URL(env.baseURL).origin })
  await context.setGeolocation(nearby(city))
}

export function textFile(): { name: string; mimeType: string; buffer: Buffer } {
  return { name: "notes.txt", mimeType: "text/plain", buffer: Buffer.from("not an image\n") }
}

export function oversizedImage(): { name: string; mimeType: string; buffer: Buffer } {
  return {
    name: "too-large.png",
    mimeType: "image/png",
    buffer: Buffer.alloc(env.maxUploadBytes + 1, 0),
  }
}
