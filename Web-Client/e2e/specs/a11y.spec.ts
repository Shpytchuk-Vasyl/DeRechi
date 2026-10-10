import AxeBuilder from "@axe-core/playwright"
import type { Page, TestInfo } from "@playwright/test"
import { expect, test } from "../fixtures/base"
import { ClaimCard, Toasts } from "../fixtures/pages"
import { openDetails, openHome, openList, openSafety, walkReport } from "../support/key-pages"
import { allowDocumentStatus } from "../support/site"

const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]
const BLOCKING = new Set(["serious", "critical"])

const KNOWN: { rule: string; html: RegExp; reason: string }[] = [
  {
    rule: "color-contrast",
    html: /opacity-70/,
    reason: "muted text (card meta, list counter, form hints) is drawn at 70% opacity",
  },
]

type Violation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number]

function withoutKnown(violation: Violation): Violation {
  const known = KNOWN.filter((it) => it.rule === violation.id)
  const nodes = violation.nodes.filter((node) => !known.some((it) => it.html.test(node.html)))
  return { ...violation, nodes }
}

function describe(violation: Violation): string {
  const targets = violation.nodes
    .slice(0, 5)
    .map((node) => node.target.join(" "))
    .join(" | ")
  return `${violation.id} (${violation.impact}): ${violation.help} [${violation.nodes.length}× ${targets}]`
}

async function expectAccessible(page: Page, testInfo: TestInfo, label: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(TAGS).analyze()
  await testInfo.attach(`axe ${label}`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: "application/json",
  })
  const blocking = results.violations
    .filter((it) => BLOCKING.has(it.impact ?? ""))
    .map(withoutKnown)
    .filter((it) => it.nodes.length > 0)
  expect.soft(blocking.map(describe), `serious/critical axe violations: ${label}`).toEqual([])
}

test.describe("accessibility", () => {
  test("home", async ({ page, t, go, shared }, testInfo) => {
    await openHome({ page, t, go, shared })
    await expectAccessible(page, testInfo, "home")
  })

  test("list", async ({ page, t, go, shared }, testInfo) => {
    await openList({ page, t, go, shared }, shared.items.foundPhoto)
    await expectAccessible(page, testInfo, "list")
  })

  test("details", async ({ page, t, go, shared }, testInfo) => {
    await openDetails({ page, t, go, shared }, shared.items.foundPhoto)
    await expectAccessible(page, testInfo, "details")
  })

  test("every report step", async ({ page, t, go, shared }, testInfo) => {
    await walkReport({ page, t, go, shared }, (label) => expectAccessible(page, testInfo, label))
  })

  test("claim form", async ({ page, t, go, shared, flags }, testInfo) => {
    await openDetails({ page, t, go, shared }, shared.items.claimLost)
    await new ClaimCard(page, t, "lost").open()
    await new Toasts(page, t, flags).dismissSmsOutage()
    await expectAccessible(page, testInfo, "claim form")
  })

  test("safety", async ({ page, t, go, shared }, testInfo) => {
    await openSafety({ page, t, go, shared })
    await expectAccessible(page, testInfo, "safety")
  })

  test("404", async ({ page, t, go, consoleErrors }, testInfo) => {
    allowDocumentStatus(consoleErrors, 404)
    await go("/nope-a11y")
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(t("error.notFoundTitle"))
    await expectAccessible(page, testInfo, "404")
  })
})
