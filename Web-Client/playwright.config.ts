import { defineConfig, devices, type PlaywrightTestConfig } from "@playwright/test"
import type { TestOptions } from "./e2e/fixtures/base"
import { env } from "./e2e/support/env"

const SWEEP = /@layout-sweep/

type AppProject = NonNullable<PlaywrightTestConfig<TestOptions>["projects"]>[number]

const projects: AppProject[] = [
  {
    name: "desktop",
    use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 }, locale: "uk" },
    grepInvert: [/@mobile-only/, /@tablet-only/, SWEEP],
  },
  {
    name: "mobile",
    use: { ...devices["Pixel 7"], locale: "uk" },
    grep: [/@responsive/, /@mobile-only/],
    grepInvert: [SWEEP],
  },
  {
    name: "tablet",
    use: {
      ...devices["Desktop Chrome"],
      viewport: { width: 768, height: 1024 },
      hasTouch: true,
      locale: "uk",
    },
    grep: [/@responsive/, /@tablet-only/],
    grepInvert: [SWEEP],
  },
]

if (process.env.E2E_SAFARI)
  projects.push(
    {
      name: "safari",
      use: { ...devices["Desktop Safari"], viewport: { width: 1440, height: 900 }, locale: "uk" },
      grepInvert: [/@mobile-only/, /@tablet-only/, SWEEP],
    },
    {
      name: "safari-iphone",
      use: { ...devices["iPhone 14"], locale: "uk" },
      grep: [/@responsive/, /@mobile-only/],
      grepInvert: [SWEEP],
    },
  )

if (process.env.E2E_LAYOUT_SWEEP)
  projects.push({
    name: "layout-sweep",
    use: {
      ...devices["Desktop Chrome"],
      viewport: { width: 1440, height: 900 },
      locale: "uk",
    },
    grep: /@layout-sweep/,
  })

export default defineConfig<TestOptions>({
  testDir: "./e2e/specs",
  outputDir: "./test-results",
  fullyParallel: true,
  workers: Number(process.env.E2E_WORKERS ?? 2),
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  globalSetup: "./e2e/global-setup.ts",
  use: {
    baseURL: env.baseURL,
    reducedMotion: "reduce",
    timezoneId: "Europe/Kyiv",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  webServer: {
    command: "pnpm start",
    url: env.baseURL,
    reuseExistingServer: false,
    timeout: 30_000,
  },
  projects,
})
