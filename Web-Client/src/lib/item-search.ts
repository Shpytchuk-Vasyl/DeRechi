import type { Category } from "@/api/items"
import type { ItemFilterInput, ItemSort } from "@/graphql/generated/graphql"
import { ISO_DATE_PATTERN } from "@/lib/dates"

export const SORTS: ItemSort[] = ["DATE_DESC", "DATE_ASC", "TITLE_ASC", "TITLE_DESC"]

export const NEAR_RADIUS_KM = 10

export type Coordinates = { lat: number; lon: number }

export function roundCoordinate(value: number): number {
  return Math.round(value * 1000) / 1000
}

export type ItemSearch = {
  search?: string
  category?: string
  dateFrom?: string
  dateTo?: string
  near?: Coordinates
  sort: ItemSort
}

export type RawSearchParams = Record<string, string | string[] | undefined>

function single(value: string | string[] | undefined): string | undefined {
  const first = Array.isArray(value) ? value[0] : value
  const trimmed = first?.trim()
  return trimmed ? trimmed : undefined
}

function date(value: string | string[] | undefined): string | undefined {
  const raw = single(value)
  return raw && ISO_DATE_PATTERN.test(raw) ? raw : undefined
}

function coordinate(value: string | string[] | undefined, limit: number): number | undefined {
  const raw = single(value)
  if (!raw) return undefined
  const parsed = Number(raw)
  return Number.isFinite(parsed) && Math.abs(parsed) <= limit ? parsed : undefined
}

export function parseItemSearch(raw: RawSearchParams): ItemSearch {
  const sort = single(raw.sort)
  const lat = coordinate(raw.lat, 90)
  const lon = coordinate(raw.lon, 180)

  return {
    search: single(raw.search),
    category: single(raw.category),
    dateFrom: date(raw.dateFrom),
    dateTo: date(raw.dateTo),
    near: lat !== undefined && lon !== undefined ? { lat, lon } : undefined,
    sort: SORTS.includes(sort as ItemSort) ? (sort as ItemSort) : "DATE_DESC",
  }
}

export function toFilter(search: ItemSearch, categories: Category[]): ItemFilterInput {
  const category = categories.find((it) => it.key === search.category)

  return {
    search: search.search ?? null,
    categoryId: category?.id ?? null,
    dateFrom: search.dateFrom ?? null,
    dateTo: search.dateTo ?? null,
    near: search.near ? { ...search.near, radiusKm: NEAR_RADIUS_KM } : null,
  }
}

export function hasActiveFilters(search: ItemSearch): boolean {
  return Boolean(search.search || search.category || search.dateFrom || search.dateTo)
}

export function toQuery(search: ItemSearch): Record<string, string> {
  const query: Record<string, string> = {}
  if (search.search) query.search = search.search
  if (search.category) query.category = search.category
  if (search.dateFrom) query.dateFrom = search.dateFrom
  if (search.dateTo) query.dateTo = search.dateTo
  if (search.near) {
    query.lat = String(search.near.lat)
    query.lon = String(search.near.lon)
  }
  if (search.sort !== "DATE_DESC") query.sort = search.sort
  return query
}
