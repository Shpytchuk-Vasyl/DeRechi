import type { DeepPartial } from "react-hook-form"
import type { ItemKind } from "@/api/items"
import type { ReportDraft } from "@/schema/report-schema"

type Area = "local" | "session"

function storage(area: Area): Storage | undefined {
  try {
    if (typeof window === "undefined") return undefined
    return area === "local" ? window.localStorage : window.sessionStorage
  } catch {
    return undefined
  }
}

function read(area: Area, key: string): string | null | undefined {
  try {
    const store = storage(area)
    return store ? store.getItem(key) : undefined
  } catch {
    return undefined
  }
}

function write(area: Area, key: string, value: string): void {
  try {
    storage(area)?.setItem(key, value)
  } catch {}
}

function remove(area: Area, key: string): void {
  try {
    storage(area)?.removeItem(key)
  } catch {}
}

// Report form draft: survives a reload of the tab for a few minutes, not a new visit.

const DRAFT_TTL_MS = 5 * 60_000

export type SavedDraft = { at: number; step: number; values: DeepPartial<ReportDraft> }

const draftKey = (kind: ItemKind) => `report-draft:${kind}`

export function saveReportDraft(
  kind: ItemKind,
  step: number,
  values: DeepPartial<ReportDraft>,
  now = Date.now(),
): void {
  const draft: SavedDraft = { at: now, step, values }
  write("session", draftKey(kind), JSON.stringify(draft))
}

export function loadReportDraft(kind: ItemKind, now = Date.now()): SavedDraft | null {
  const raw = read("session", draftKey(kind))
  if (!raw) return null
  try {
    const saved = JSON.parse(raw) as SavedDraft
    if (now - saved.at > DRAFT_TTL_MS) {
      remove("session", draftKey(kind))
      return null
    }
    return saved
  } catch {
    return null
  }
}

export function clearReportDraft(kind: ItemKind): void {
  remove("session", draftKey(kind))
}

// The notice this tab has just published, so its page can ask to share it.

const JUST_PUBLISHED = "derechi:just-published"

export function markJustPublished(kind: ItemKind, id: string): void {
  write("session", JUST_PUBLISHED, `${kind}:${id}`)
}

export function isJustPublished(kind: ItemKind, id: string): boolean {
  return read("session", JUST_PUBLISHED) === `${kind}:${id}`
}

export function forgetJustPublished(): void {
  remove("session", JUST_PUBLISHED)
}

// Guided tours already seen. Without storage a tour counts as seen, so it does not reopen on
// every visit.

const TOUR_PREFIX = "derechi.tour."

export function hasSeenTour(id: string): boolean {
  const value = read("local", TOUR_PREFIX + id)
  return value === undefined || value === "done"
}

export function rememberTour(id: string): void {
  write("local", TOUR_PREFIX + id, "done")
}

// Mascot catches, counted up to the prize and then from zero. Without storage nothing counts.

const CAUGHT_KEY = "znaida-caught"

export function recordMascotCatch(catches: number): { count: number; prize: boolean } {
  const stored = read("local", CAUGHT_KEY)
  if (stored === undefined) return { count: 0, prize: false }
  const count = (Number(stored) || 0) + 1
  write("local", CAUGHT_KEY, String(count >= catches ? 0 : count))
  return { count, prize: count >= catches }
}

const CITY_HINT_KEY = "derechi.city-hint"

const CITY_HINT_PAUSE_MS = 60 * 60_000

export function isCityHintPaused(now = Date.now()): boolean {
  const stored = read("local", CITY_HINT_KEY)
  if (stored === undefined) return true
  const closedAt = Number(stored)
  return stored !== null && Number.isFinite(closedAt) && now - closedAt < CITY_HINT_PAUSE_MS
}

export function pauseCityHint(now = Date.now()): void {
  write("local", CITY_HINT_KEY, String(now))
}
