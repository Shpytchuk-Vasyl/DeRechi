import { createHash } from "node:crypto"
import { existsSync, readFileSync } from "node:fs"
import path from "node:path"
import type { Claim, ContactInput, ItemInput } from "./api"
import * as api from "./api"

export const STATE_FILE = path.resolve(__dirname, "../.state/run.json")
export const EMAIL_DOMAIN = "e2e.derechi.test"
export const LOCALES = ["en", "uk", "pl", "de", "fr"] as const
export type Locale = (typeof LOCALES)[number]
export type ItemKind = "lost" | "found"

export function newRunId(now = new Date()): string {
  const two = (value: number) => String(value).padStart(2, "0")
  const stamp = [
    two(now.getFullYear() % 100),
    two(now.getMonth() + 1),
    two(now.getDate()),
    two(now.getHours()),
    two(now.getMinutes()),
  ].join("")
  const random = Math.floor(Math.random() * 36 ** 3)
    .toString(36)
    .padStart(3, "0")
  return `e2e${stamp}${random}`
}

export function runId(): string {
  if (process.env.E2E_RUN_ID) return process.env.E2E_RUN_ID
  if (existsSync(STATE_FILE)) {
    const state = JSON.parse(readFileSync(STATE_FILE, "utf8")) as { runId?: string }
    if (state.runId) {
      process.env.E2E_RUN_ID = state.runId
      return state.runId
    }
  }
  throw new Error("e2e: no RUN_ID; global-setup did not run")
}

let sequence = 0

export type Token = {
  value: string
  worker: number
  seq: number
}

export function nextToken(workerIndex: number): Token {
  const seq = sequence++
  const worker = workerIndex.toString(36).padStart(2, "0").slice(-2)
  const counter = seq.toString(36).padStart(3, "0").slice(-3)
  return { value: `${runId()}-${worker}${counter}`, worker: workerIndex, seq }
}

const KYIV = "Europe/Kyiv"

export function kyivDate(offsetDays = 0, now = new Date()): string {
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: KYIV,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now)
  const [year, month, day] = today.split("-").map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + offsetDays))
  return shifted.toISOString().slice(0, 10)
}

export function shiftDay(isoDay: string, days: number): string {
  const [y, m, d] = isoDay.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10)
}

export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)
}

export type City = "lviv" | "kyiv" | "warsaw"

export const CITIES: Record<
  City,
  { lat: number; lon: number; countryCode: string; names: Record<Locale, string> }
> = {
  lviv: {
    lat: 49.8397,
    lon: 24.0297,
    countryCode: "UA",
    names: { en: "Lviv", uk: "Львів", pl: "Lwów", de: "Lwiw", fr: "Lviv" },
  },
  kyiv: {
    lat: 50.4501,
    lon: 30.5234,
    countryCode: "UA",
    names: { en: "Kyiv", uk: "Київ", pl: "Kijów", de: "Kyjiw", fr: "Kyiv" },
  },
  warsaw: {
    lat: 52.2297,
    lon: 21.0122,
    countryCode: "PL",
    names: { en: "Warsaw", uk: "Варшава", pl: "Warszawa", de: "Warschau", fr: "Varsovie" },
  },
}

export type CategoryKey =
  | "DOCUMENTS"
  | "WALLET"
  | "ELECTRONICS"
  | "JEWELRY"
  | "ANIMALS"
  | "KEYS"
  | "BAGS"
  | "OTHER"

export const ITEM_NAMES: Record<Locale, Record<CategoryKey, string>> = {
  en: {
    DOCUMENTS: "Passport in a blue cover",
    WALLET: "Black leather wallet",
    ELECTRONICS: "Phone in a green case",
    JEWELRY: "Silver ring",
    ANIMALS: "Ginger cat with a collar",
    KEYS: "Keys with a red keyring",
    BAGS: "Grey backpack",
    OTHER: "Yellow umbrella",
  },
  uk: {
    DOCUMENTS: "Паспорт у синій обкладинці",
    WALLET: "Чорний шкіряний гаманець",
    ELECTRONICS: "Телефон у зеленому чохлі",
    JEWELRY: "Срібна каблучка",
    ANIMALS: "Рудий кіт з нашийником",
    KEYS: "Ключі з червоним брелоком",
    BAGS: "Сірий рюкзак",
    OTHER: "Жовта парасолька",
  },
  pl: {
    DOCUMENTS: "Paszport w niebieskiej okładce",
    WALLET: "Czarny skórzany portfel",
    ELECTRONICS: "Telefon w zielonym etui",
    JEWELRY: "Srebrny pierścionek",
    ANIMALS: "Rudy kot z obrożą",
    KEYS: "Klucze z czerwonym brelokiem",
    BAGS: "Szary plecak",
    OTHER: "Żółty parasol",
  },
  de: {
    DOCUMENTS: "Reisepass in blauer Hülle",
    WALLET: "Schwarze Ledergeldbörse",
    ELECTRONICS: "Handy in grüner Hülle",
    JEWELRY: "Silberner Ring",
    ANIMALS: "Rote Katze mit Halsband",
    KEYS: "Schlüssel mit rotem Anhänger",
    BAGS: "Grauer Rucksack",
    OTHER: "Gelber Regenschirm",
  },
  fr: {
    DOCUMENTS: "Passeport sous couverture bleue",
    WALLET: "Portefeuille en cuir noir",
    ELECTRONICS: "Téléphone dans une coque verte",
    JEWELRY: "Bague en argent",
    ANIMALS: "Chat roux avec un collier",
    KEYS: "Clés avec un porte-clés rouge",
    BAGS: "Sac à dos gris",
    OTHER: "Parapluie jaune",
  },
}

export const MAX_TITLE = 100
export const MAX_DESCRIPTION = 250

export function itemTitle(token: string, name: string): string {
  return `${token} ${name}`.slice(0, MAX_TITLE).trim()
}

const MAX_EMAIL = 50

function runDigit(): number {
  return createHash("sha256").update(runId()).digest()[0] % 10
}

export function phoneFor(token: Token, n: number): string {
  const worker = String(token.worker % 100).padStart(2, "0")
  const seq = String(token.seq % 1000).padStart(3, "0")
  return `+38067${runDigit()}${worker}${seq}${n % 10}`
}

export function emailFor(role: string, token: string, n: number): string {
  const tail = `-${token}-${n}@${EMAIL_DOMAIN}`
  const head = role
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "")
    .slice(0, Math.max(1, MAX_EMAIL - tail.length))
  const email = `${head}${tail}`
  if (email.length > MAX_EMAIL) throw new Error(`e2e: ${email} is longer than ${MAX_EMAIL}`)
  return email
}

export const DESCRIPTIONS: Record<Locale, string> = {
  en: "Left on a bench near the main entrance, in the afternoon.",
  uk: "Залишили на лавці біля головного входу, по обіді.",
  pl: "Zostawione na ławce przy głównym wejściu, po południu.",
  de: "Am Nachmittag auf einer Bank am Haupteingang liegen geblieben.",
  fr: "Oublié sur un banc près de l'entrée principale, l'après-midi.",
}

export type CreatedItem = {
  kind: ItemKind
  id: string
  title: string
  path: string
  category: CategoryKey
  input: ItemInput
}

export type CreatedClaim = Claim & { contact: ContactInput }

export class DataFactory {
  private contacts = 0

  constructor(
    readonly token: Token,
    readonly locale: Locale,
  ) {}

  get value(): string {
    return this.token.value
  }

  title(name: string): string {
    return itemTitle(this.token.value, name)
  }

  contact(role = "claimant", overrides: Partial<ContactInput> = {}): ContactInput {
    const n = this.contacts++
    return {
      phone: phoneFor(this.token, n),
      email: emailFor(role, this.token.value, n),
      socialMedias: [],
      ...overrides,
    }
  }

  async claim(kind: ItemKind, itemId: string, contact?: ContactInput): Promise<CreatedClaim> {
    const used = contact ?? this.contact("claimant")
    const result = await api.claim(kind, itemId, used)
    return { ...result, contact: used }
  }
}
