import { expect, type Locator, type Page } from "@playwright/test"

export const MOBILE_MAX = 767

export const SHEET_MAX = 900

export const WIDE_MIN = 1024

function width(page: Page): number {
  const viewport = page.viewportSize()
  if (!viewport) throw new Error("e2e: the page has no fixed viewport")
  return viewport.width
}

export function isMobileLayout(page: Page): boolean {
  return width(page) <= MOBILE_MAX
}

export function isSheet(page: Page): boolean {
  return width(page) <= SHEET_MAX
}

export function isWide(page: Page): boolean {
  return width(page) >= WIDE_MIN
}

export function horizontalOverflow(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const root = document.documentElement
    const limit = window.innerWidth
    if (root.scrollWidth <= limit) return []

    const problems = [`scrollWidth ${root.scrollWidth} > innerWidth ${limit}`]
    const clipped = (element: Element): boolean => {
      for (
        let node = element.parentElement;
        node && node !== document.body;
        node = node.parentElement
      ) {
        const style = getComputedStyle(node)
        if (style.position === "fixed") return true
        if (style.overflowX !== "visible") return true
      }
      return false
    }
    for (const element of Array.from(document.body.querySelectorAll("*"))) {
      const box = element.getBoundingClientRect()
      if (box.width === 0 || box.right <= limit + 1 || clipped(element)) continue
      const id = element.id ? `#${element.id}` : ""
      const classes =
        typeof element.className === "string" && element.className
          ? `.${element.className.trim().split(/\s+/).slice(0, 3).join(".")}`
          : ""
      problems.push(
        `${element.tagName.toLowerCase()}${id}${classes} right=${Math.round(box.right)}`,
      )
      if (problems.length > 6) break
    }
    return problems
  })
}

export async function expectNoHorizontalScroll(page: Page, label: string): Promise<void> {
  await expect
    .soft(async () => expect(await horizontalOverflow(page)).toEqual([]), {
      message: `no horizontal scroll: ${label}`,
    })
    .toPass({ timeout: 5_000 })
}

export type DialogShape = "sheet" | "centered" | "other"

export async function dialogShape(page: Page, dialog: Locator): Promise<DialogShape> {
  const box = await dialog.boundingBox()
  if (!box) return "other"
  const { width, height } = await page.evaluate(() => ({
    width: document.documentElement.clientWidth,
    height: document.documentElement.clientHeight,
  }))
  const near = (a: number, b: number, tolerance = 2) => Math.abs(a - b) <= tolerance
  if (near(box.x, 0) && near(box.width, width) && near(box.y + box.height, height)) return "sheet"
  if (
    box.width < width &&
    near(box.x + box.width / 2, width / 2) &&
    near(box.y + box.height / 2, height / 2)
  )
    return "centered"
  return "other"
}

export type Placement = "beside" | "below" | "other"

export async function placement(first: Locator, second: Locator): Promise<Placement> {
  const [a, b] = await Promise.all([first.boundingBox(), second.boundingBox()])
  if (!a || !b) return "other"
  if (b.x >= a.x + a.width - 1) return "beside"
  if (b.y >= a.y + a.height - 1) return "below"
  return "other"
}
