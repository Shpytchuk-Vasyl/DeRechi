import { test as base, type ConsoleMessage, type Page, type Response } from "@playwright/test"
import { DataFactory, type Locale, nextToken } from "../support/data"
import { type AllowedConsoleError, allowedConsoleErrors, type Flags, flags } from "../support/env"
import { recordForeignHosts } from "../support/hosts"
import { waitForHydration } from "../support/hydration"
import { type Translator, translator } from "../support/i18n"
import { readShared, type SharedData } from "../support/shared"

export type TestOptions = {
  appLocale: Locale
}

export type ConsoleErrors = {
  allow(pattern: RegExp, reason: string): void
  readonly list: string[]
}

type Fixtures = {
  t: Translator
  token: string
  data: DataFactory
  flags: Flags
  go: (path: string, options?: Parameters<Page["goto"]>[1]) => Promise<Response | null>
  consoleErrors: ConsoleErrors
  guard: void
}

type WorkerFixtures = {
  shared: SharedData
}

const BOTID_SCRIPT = "**/a-4-a/c.js*"
const BOTID_STUB = "window.V_C=window.V_C||[];window.V_C.push({b:1})"

function describe(message: ConsoleMessage): string {
  return `${message.text()} @ ${message.location().url}`
}

export const test = base.extend<TestOptions & Fixtures, WorkerFixtures>({
  appLocale: ["uk", { option: true }],

  t: async ({ appLocale }, use) => {
    await use(translator(appLocale))
  },

  token: async ({ data }, use) => {
    await use(data.value)
  },

  data: async ({ appLocale }, use, testInfo) => {
    await use(new DataFactory(nextToken(testInfo.workerIndex), appLocale))
  },

  flags: async ({}, use) => {
    await use(flags)
  },

  go: async ({ page, appLocale }, use) => {
    await use(async (path, options) => {
      if (!path.startsWith("/")) throw new Error(`go(): "${path}" must start with /`)
      const response = await page.goto(`/${appLocale}${path === "/" ? "" : path}`, options)
      await waitForHydration(page)
      return response
    })
  },

  consoleErrors: async ({}, use) => {
    const allowed: AllowedConsoleError[] = [...allowedConsoleErrors]
    const list: string[] = []
    const errors: ConsoleErrors & { add(line: string): void } = {
      allow(pattern, reason) {
        allowed.push({ pattern, reason })
      },
      get list() {
        return list.filter((line) => !allowed.some((entry) => entry.pattern.test(line)))
      },
      add(line) {
        list.push(line)
      },
    }
    await use(errors)
  },

  guard: [
    async ({ context, consoleErrors }, use, testInfo) => {
      const collect = consoleErrors as ConsoleErrors & { add(line: string): void }
      const foreign = recordForeignHosts(context)

      await context.route(BOTID_SCRIPT, (route) =>
        route.fulfill({ status: 200, contentType: "application/javascript", body: BOTID_STUB }),
      )
      await context.route(/^https?:\/\/maps\.(googleapis|gstatic)\.com\//, (route) =>
        route.abort("blockedbyclient"),
      )

      const watch = (page: Page) => {
        page.on("console", (message) => {
          if (message.type() === "error") collect.add(describe(message))
        })
        page.on("pageerror", (error) => collect.add(`pageerror: ${error.message}`))
      }
      for (const page of context.pages()) watch(page)
      context.on("page", watch)

      await use()

      for (const host of foreign()) {
        testInfo.annotations.push({ type: "external-host", description: host })
      }

      const unexpected = consoleErrors.list
      if (unexpected.length === 0) return
      const report = unexpected.map((line) => `  - ${line}`).join("\n")
      if (testInfo.status !== testInfo.expectedStatus) {
        testInfo.annotations.push({ type: "console-errors", description: report })
        return
      }
      throw new Error(`Unexpected console errors (allow them in env.ts with a reason):\n${report}`)
    },
    { auto: true, box: true },
  ],

  shared: [
    async ({}, use) => {
      await use(readShared())
    },
    { scope: "worker" },
  ],
})

export { expect } from "@playwright/test"
