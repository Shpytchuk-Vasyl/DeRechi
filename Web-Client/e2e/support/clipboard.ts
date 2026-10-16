import type { Page } from "@playwright/test"

type RecordingWindow = typeof window & { __e2eClipboardWrites: string[] }

export async function recordClipboardWrites(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    if (window !== window.top) return
    const writes: string[] = []
    Object.defineProperty(window, "__e2eClipboardWrites", { value: writes })
    navigator.clipboard.writeText = async (text: string) => {
      writes.push(text)
    }
  })
  return () => page.evaluate(() => [...(window as RecordingWindow).__e2eClipboardWrites])
}
