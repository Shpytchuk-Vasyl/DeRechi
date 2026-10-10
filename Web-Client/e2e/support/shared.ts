import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs"
import path from "node:path"
import type { ItemInput, PlaceInput } from "./api"
import * as api from "./api"
import {
  type CategoryKey,
  CITIES,
  type City,
  type CreatedItem,
  DESCRIPTIONS,
  daysBetween,
  emailFor,
  ITEM_NAMES,
  type ItemKind,
  kyivDate,
  shiftDay,
} from "./data"
import { env } from "./env"
import { missingPhotoKey, photoKey, uploadPhoto } from "./minio"

export const SHARED_FILE = path.resolve(__dirname, "../.state/shared.json")

export const SHARED_GROUPS = [
  "view",
  "cnt1x",
  "cnt3x",
  "cnt5x",
  "page",
  "sort",
  "cat",
  "date",
  "near",
  "claim",
] as const
export type SharedGroup = (typeof SHARED_GROUPS)[number]

export type SharedItemKey =
  | "lostFull"
  | "foundPhoto"
  | "lostNoPhoto"
  | "foundBrokenPhoto"
  | "lostStale"
  | "lostRewardPL"
  | "lostNoReward"
  | "lostZeroReward"
  | "foundDocuments"
  | "claimLost"
  | "claimFound"

export type SharedItem = CreatedItem & {
  group: SharedGroup
  key: string
}

export type SharedData = {
  marker: string
  anchor: string
  seededAt: string
  created: number
  search: Record<SharedGroup, string>
  place: PlaceInput
  places: Record<City, PlaceInput>
  items: Record<SharedItemKey, SharedItem>
  groups: Record<SharedGroup, SharedItem[]>
}

export function isoWeek(day: string): { year: number; week: number } {
  const [y, m, d] = day.split("-").map(Number)
  const date = new Date(Date.UTC(y, m - 1, d))
  date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7))
  const year = date.getUTCFullYear()
  const dayOfYear = (date.getTime() - Date.UTC(year, 0, 1)) / 86_400_000 + 1
  return { year, week: Math.ceil(dayOfYear / 7) }
}

export function sharedMarker(now = new Date(), suffix = env.sharedSuffix): string {
  if (!/^[a-z0-9]{0,6}$/.test(suffix)) {
    throw new Error(
      `e2e: E2E_SHARED_SUFFIX "${suffix}" must be up to 6 lowercase letters or digits`,
    )
  }
  const { year, week } = isoWeek(kyivDate(0, now))
  const two = (value: number) => String(value).padStart(2, "0")
  return `e2eshared${two(year % 100)}${two(week)}${suffix}`
}

type Definition = {
  group: SharedGroup
  key: string
  kind: ItemKind
  category: CategoryKey
  name?: string
  offset: number
  description: string | null
  compensation: { amount: number; currency?: string } | null
  image: "upload" | "missing" | null
  city: City
}

const DESCRIPTION = DESCRIPTIONS.uk

function def(
  group: SharedGroup,
  key: string,
  kind: ItemKind,
  extra: Partial<Omit<Definition, "group" | "key" | "kind">> = {},
): Definition {
  return {
    group,
    key,
    kind,
    category: "WALLET",
    offset: -2,
    description: DESCRIPTION,
    compensation: null,
    image: kind === "found" ? "upload" : null,
    city: "lviv",
    ...extra,
  }
}

function numbered(group: SharedGroup, kind: ItemKind, count: number): Definition[] {
  return Array.from({ length: count }, (_, index) => {
    const key = String(index + 1).padStart(2, "0")
    return def(group, key, kind, { name: `${ITEM_NAMES.uk.WALLET} ${index + 1}` })
  })
}

const DEFINITIONS: Definition[] = [
  def("view", "lostFull", "lost", { category: "KEYS", compensation: { amount: 500 } }),
  def("view", "foundPhoto", "found", { category: "WALLET" }),
  def("view", "lostNoPhoto", "lost", { category: "BAGS", description: null }),
  def("view", "foundBrokenPhoto", "found", { category: "JEWELRY", image: "missing" }),
  def("view", "lostStale", "lost", { category: "OTHER", offset: -20 }),
  def("view", "lostRewardPL", "lost", { compensation: { amount: 500 }, city: "warsaw" }),
  def("view", "lostNoReward", "lost", { category: "ELECTRONICS" }),
  def("view", "lostZeroReward", "lost", { category: "ANIMALS", compensation: { amount: 0 } }),
  def("view", "foundDocuments", "found", { category: "DOCUMENTS" }),
  ...numbered("cnt1x", "lost", 1),
  ...numbered("cnt3x", "found", 3),
  ...numbered("cnt5x", "lost", 5),
  ...numbered("page", "lost", 17),
  def("sort", "A", "lost", { offset: -5 }),
  def("sort", "B", "lost", { offset: -1 }),
  def("sort", "C", "lost", { offset: -3 }),
  def("cat", "wallet", "lost", { category: "WALLET" }),
  def("cat", "keys", "lost", { category: "KEYS" }),
  def("date", "d2", "lost", { offset: -2 }),
  def("date", "d10", "lost", { offset: -10 }),
  def("date", "d20", "lost", { offset: -20 }),
  def("near", "lviv", "found", { city: "lviv" }),
  def("near", "kyiv", "found", { city: "kyiv" }),
  def("claim", "claimLost", "lost"),
  def("claim", "claimFound", "found"),
]

const NAMED: readonly SharedItemKey[] = [
  "lostFull",
  "foundPhoto",
  "lostNoPhoto",
  "foundBrokenPhoto",
  "lostStale",
  "lostRewardPL",
  "lostNoReward",
  "lostZeroReward",
  "foundDocuments",
  "claimLost",
  "claimFound",
]

function places(marker: string): Record<City, PlaceInput> {
  const place = (city: City): PlaceInput => ({
    id: `e2e-${marker}-${city}`,
    name: `${marker} ${CITIES[city].names.uk}`,
    lat: CITIES[city].lat,
    lon: CITIES[city].lon,
    countryCode: CITIES[city].countryCode,
  })
  return { lviv: place("lviv"), kyiv: place("kyiv"), warsaw: place("warsaw") }
}

function titleOf(marker: string, definition: Definition): string {
  const name = definition.name ?? ITEM_NAMES.uk[definition.category]
  return `${marker}-${definition.group}-${definition.key} ${name}`
}

function inputOf(
  marker: string,
  definition: Definition,
  serial: number,
  anchor: string,
  categoryIds: Map<string, string>,
  placeOf: Record<City, PlaceInput>,
): ItemInput {
  const categoryId = categoryIds.get(definition.category)
  if (!categoryId) throw new Error(`e2e: the API has no category ${definition.category}`)
  return {
    title: titleOf(marker, definition),
    description: definition.description,
    date: shiftDay(anchor, definition.offset),
    compensation: definition.compensation,
    image:
      definition.image === "upload"
        ? photoKey(marker)
        : definition.image === "missing"
          ? missingPhotoKey(marker)
          : null,
    categoryId,
    place: placeOf[definition.city],
    contact: {
      phone: `+38067990${String(serial).padStart(4, "0")}`,
      email: emailFor("author", marker, serial),
      socialMedias: [],
    },
  }
}

type Stored = Omit<api.Item, "contact">

async function lookup(kind: ItemKind, search: string): Promise<Stored[]> {
  const page = await api.items(kind, { search }, { first: 100 })
  if (page.hasNextPage) throw new Error(`e2e: more than 100 ${kind} items match "${search}"`)
  return page.items
}

function differences(stored: Stored, input: ItemInput, currency: string | null): string[] {
  const found: string[] = []
  const check = (field: string, actual: unknown, expected: unknown) => {
    if (JSON.stringify(actual) !== JSON.stringify(expected))
      found.push(`${field} ${JSON.stringify(actual)} != ${JSON.stringify(expected)}`)
  }
  check("category", stored.category.id, input.categoryId)
  check("description", stored.description, input.description ?? null)
  check("date", stored.date, input.date)
  check("image", stored.image, input.image ?? null)
  check("place", stored.place.id, input.place.id)
  check(
    "compensation",
    stored.compensation,
    input.compensation ? { amount: input.compensation.amount, currency } : null,
  )
  return found
}

export async function seedShared(now = new Date()): Promise<SharedData> {
  const marker = sharedMarker(now)
  const today = kyivDate(0, now)
  const placeOf = places(marker)
  const search = Object.fromEntries(
    SHARED_GROUPS.map((group) => [group, `${marker}-${group}`]),
  ) as Record<SharedGroup, string>

  const [categories, countries] = await Promise.all([api.categories(), api.countries()])
  const categoryIds = new Map(categories.map((category) => [category.key, category.id]))
  const currencyOf = new Map(countries.map((country) => [country.code, country.currency]))

  const stored = new Map<string, Stored[]>()
  for (const group of SHARED_GROUPS) {
    const kinds = new Set(DEFINITIONS.filter((it) => it.group === group).map((it) => it.kind))
    for (const kind of kinds) stored.set(`${group}/${kind}`, await lookup(kind, search[group]))
  }

  const problems: string[] = []
  const matches = DEFINITIONS.map((definition) => {
    const title = titleOf(marker, definition)
    const rows = (stored.get(`${definition.group}/${definition.kind}`) ?? []).filter(
      (row) => row.title === title,
    )
    if (rows.length > 1) problems.push(`${rows.length} ${definition.kind} items titled "${title}"`)
    return rows[0]
  })
  const known = new Set(DEFINITIONS.map((it) => titleOf(marker, it)))
  for (const [group, rows] of stored) {
    for (const row of rows)
      if (!known.has(row.title)) problems.push(`${group}: unexpected item "${row.title}"`)
  }

  const firstIndex = matches.findIndex((row) => row !== undefined)
  const anchor =
    firstIndex < 0
      ? today
      : shiftDay(matches[firstIndex]?.date ?? today, -DEFINITIONS[firstIndex].offset)
  const oldest = Math.min(...DEFINITIONS.map((it) => it.offset))
  const missing = matches.some((row) => row === undefined)
  if (
    missing &&
    (daysBetween(anchor, today) < 0 || daysBetween(shiftDay(anchor, oldest), today) > 30)
  )
    problems.push(`anchor ${anchor} is too far from today ${today} to add the missing notices`)

  const inputs = DEFINITIONS.map((definition, serial) =>
    inputOf(marker, definition, serial, anchor, categoryIds, placeOf),
  )
  matches.forEach((row, index) => {
    if (!row) return
    const input = inputs[index]
    const currency = input.compensation?.currency ?? currencyOf.get(input.place.countryCode) ?? null
    const diff = differences(row, input, currency)
    if (diff.length) problems.push(`"${row.title}": ${diff.join(", ")}`)
  })

  if (problems.length) {
    throw new Error(
      `e2e: the shared set ${marker} does not match support/shared.ts:\n` +
        problems.map((line) => `  - ${line}`).join("\n") +
        "\nSet E2E_SHARED_SUFFIX (for example E2E_SHARED_SUFFIX=b) to seed a fresh set.",
    )
  }

  if (DEFINITIONS.some((it, index) => !matches[index] && it.image === "upload")) {
    await uploadPhoto(photoKey(marker))
  }

  let created = 0
  const items: SharedItem[] = []
  for (const [index, definition] of DEFINITIONS.entries()) {
    const input = inputs[index]
    let id = matches[index]?.id
    if (!id) {
      id = (await api.createItem(definition.kind, input)).id
      created++
    }
    items.push({
      kind: definition.kind,
      id,
      title: input.title,
      path: `/${definition.kind}/${id}`,
      category: definition.category,
      input,
      group: definition.group,
      key: definition.key,
    })
  }

  const groups = Object.fromEntries(
    SHARED_GROUPS.map((group) => [group, items.filter((item) => item.group === group)]),
  ) as Record<SharedGroup, SharedItem[]>
  const named = Object.fromEntries(
    NAMED.map((key) => {
      const item = items.find(
        (it) => (it.group === "view" || it.group === "claim") && it.key === key,
      )
      if (!item) throw new Error(`e2e: no shared definition for ${key}`)
      return [key, item]
    }),
  ) as Record<SharedItemKey, SharedItem>

  const data: SharedData = {
    marker,
    anchor,
    seededAt: now.toISOString(),
    created,
    search,
    place: placeOf.lviv,
    places: placeOf,
    items: named,
    groups,
  }
  mkdirSync(path.dirname(SHARED_FILE), { recursive: true })
  writeFileSync(SHARED_FILE, `${JSON.stringify(data, null, 2)}\n`)
  return data
}

export function readShared(): SharedData {
  if (!existsSync(SHARED_FILE)) {
    throw new Error(`e2e: ${SHARED_FILE} is missing; global-setup did not seed the shared set`)
  }
  return JSON.parse(readFileSync(SHARED_FILE, "utf8")) as SharedData
}
