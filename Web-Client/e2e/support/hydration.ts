import type { Page } from "@playwright/test"

export async function waitForHydration(page: Page): Promise<void> {
  await page.waitForFunction(() => {
    if (!("__next_f" in self)) return true
    if (document.querySelector('template[id^="B:"]')) return false
    const hydrated = (selector: string) => {
      const root = document.querySelector(selector)
      const last = root
        ? Array.from(root.querySelectorAll("a, button, input, select, textarea")).at(-1)
        : undefined
      return !last || Object.keys(last).some((key) => key.startsWith("__reactProps$"))
    }
    return hydrated("main") && hydrated("header")
  })
}
