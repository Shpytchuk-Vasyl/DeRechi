import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  clearReportDraft,
  forgetJustPublished,
  hasSeenTour,
  isJustPublished,
  loadReportDraft,
  markJustPublished,
  recordMascotCatch,
  rememberTour,
  saveReportDraft,
} from "./browser-storage"

function memory(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => void values.delete(key),
    setItem: (key, value) => void values.set(key, value),
  }
}

function blocked(): Storage {
  const store = memory()
  const deny = () => {
    throw new Error("SecurityError")
  }
  store.getItem = deny
  store.setItem = deny
  store.removeItem = deny
  return store
}

let local: Storage
let session: Storage

beforeEach(() => {
  local = memory()
  session = memory()
  vi.stubGlobal("window", { localStorage: local, sessionStorage: session })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("report draft", () => {
  const values = { title: "Чорний гаманець" }

  it("gives back the draft of the same kind, under the key it has always used", () => {
    saveReportDraft("lost", 2, values, 1_000)

    expect(loadReportDraft("lost", 2_000)).toEqual({ at: 1_000, step: 2, values })
    expect(loadReportDraft("found", 2_000)).toBeNull()
    expect(session.getItem("report-draft:lost")).not.toBeNull()
  })

  it("drops a draft older than five minutes", () => {
    saveReportDraft("lost", 1, values, 0)

    expect(loadReportDraft("lost", 5 * 60_000)).not.toBeNull()
    expect(loadReportDraft("lost", 5 * 60_000 + 1)).toBeNull()
    expect(session.getItem("report-draft:lost")).toBeNull()
  })

  it("is gone once cleared", () => {
    saveReportDraft("found", 1, values)
    clearReportDraft("found")

    expect(loadReportDraft("found")).toBeNull()
  })

  it("ignores a draft that is not JSON", () => {
    session.setItem("report-draft:lost", "{broken")

    expect(loadReportDraft("lost")).toBeNull()
  })
})

describe("just published", () => {
  it("remembers only the notice that was just published", () => {
    markJustPublished("found", "65")

    expect(isJustPublished("found", "65")).toBe(true)
    expect(isJustPublished("found", "66")).toBe(false)
    expect(isJustPublished("lost", "65")).toBe(false)
  })

  it("forgets it once dismissed", () => {
    markJustPublished("lost", "7")
    forgetJustPublished()

    expect(isJustPublished("lost", "7")).toBe(false)
  })
})

describe("tours", () => {
  it("shows a tour until it has been seen", () => {
    expect(hasSeenTour("home")).toBe(false)
    rememberTour("home")

    expect(hasSeenTour("home")).toBe(true)
    expect(hasSeenTour("report")).toBe(false)
    expect(local.getItem("derechi.tour.home")).toBe("done")
  })
})

describe("mascot catches", () => {
  it("counts up to the prize and then starts again", () => {
    const counts = Array.from({ length: 6 }, () => recordMascotCatch(5))

    expect(counts.map((it) => it.count)).toEqual([1, 2, 3, 4, 5, 1])
    expect(counts.map((it) => it.prize)).toEqual([false, false, false, false, true, false])
  })
})

describe("without storage", () => {
  it("works, a little worse, when the storage throws", () => {
    vi.stubGlobal("window", { localStorage: blocked(), sessionStorage: blocked() })

    expect(() => saveReportDraft("lost", 1, {})).not.toThrow()
    expect(loadReportDraft("lost")).toBeNull()
    expect(() => markJustPublished("lost", "7")).not.toThrow()
    expect(isJustPublished("lost", "7")).toBe(false)
    expect(hasSeenTour("home")).toBe(true)
    expect(recordMascotCatch(5)).toEqual({ count: 0, prize: false })
  })

  it("works the same where there is no window at all", () => {
    vi.unstubAllGlobals()

    expect(loadReportDraft("lost")).toBeNull()
    expect(isJustPublished("lost", "7")).toBe(false)
    expect(hasSeenTour("home")).toBe(true)
    expect(recordMascotCatch(5)).toEqual({ count: 0, prize: false })
  })
})
