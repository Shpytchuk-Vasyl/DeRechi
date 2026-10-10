# Web-Client e2e (Playwright)

End-to-end tests against a **production build** of the site and the **local** backend (Getaway,
Client-API, MinIO). The suite talks to the backend **only through the GraphQL API and the
UI** (plus the presigned photo upload to MinIO, as the site does): no database access at all, and no
cleanup, since the API cannot delete anything.

## Run

```bash
pnpm e2e                              # next build, then all three projects
pnpm e2e:run --project=desktop        # no build: reuse the last one (after changing only tests)
pnpm e2e:run e2e/specs/lists.spec.ts  # one file
pnpm e2e:ui                           # Playwright UI
pnpm e2e:report                       # last HTML report
```

`pnpm e2e` runs `next build` first, then Playwright starts `next start` on :3000 itself. Stop
`pnpm dev` before: a busy port fails the run on purpose, since the slow dev server must not be
picked up instead. Two workers by default (`E2E_WORKERS`): more of them only fight over the CPU with
`next start`. First time only: `pnpm exec playwright install chromium`.

## Projects and languages

Three projects, one per viewport, **all in Ukrainian** (`appLocale` defaults to `uk`, the browser's
`locale` is `uk`):

| Project | Viewport | Runs |
|---|---|---|
| `desktop` | Desktop Chrome, 1440×900 | every test except `@mobile-only` and `@tablet-only` |
| `mobile` | Pixel 7 | `@responsive` and `@mobile-only` |
| `tablet` | Chromium, 768×1024, touch: the header nav and the inline filters (from 768 px), bottom-sheet dialogs (up to 900 px) | `@responsive` and `@tablet-only` |

**Every test runs on desktop; a test runs on mobile and tablet only when it is tagged
`@responsive`**, that is when what it checks depends on the viewport: the header nav versus the bottom
nav and the FAB, the filters bar versus the filter sheet (sort included), dialogs as bottom sheets
versus centred, the home rails' visible cards, no horizontal scroll, the report modal opened from a
list and where the report preview sits. Everything else (lists, item pages, the report form's logic,
claims, SEO, a11y, legal pages, 404) is the same code on every viewport and runs on desktop only.
`@responsive` tests never publish a notice, so a run creates each notice once.

`playwright test --list`: 178 runs, desktop 149, mobile 17, tablet 12 (3 of them `test.fixme`, all on
desktop). The catalogue with what each test checks is `e2e/TESTS.md`.

Ukrainian carries the whole suite (three plural forms, Cyrillic). The other languages are checked by
**one multilingual spec** that loops over `LOCALES` itself with `test.use({ appLocale, locale })`;
the `t` and `go` fixtures follow `appLocale`. `layout-sweep` is an extra project that exists only with
`E2E_LAYOUT_SWEEP=1`.

## Configuration

`.env.local` is read the way Next reads it (`@next/env`); `E2E_*` variables override it.

| Variable | Default | Used for |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:3000` | the site |
| `E2E_GRAPHQL_URL` | `GRAPHQL_URL` | the shared dataset, the data factory |
| `E2E_SHARED_SUFFIX` | empty | appended to the shared dataset's marker: seeds a fresh set (see below) |
| `E2E_WORKERS` | `2` | Playwright workers |
| `E2E_LAYOUT_SWEEP` | unset | adds the `layout-sweep` project |

The photo upload needs `S3_PUBLIC_ENDPOINT`, `S3_ACCESS_KEY` and `S3_SECRET_KEY` in `.env.local`. App
flags (`NEXT_PUBLIC_SMS_OUTAGE`, `S3_MAX_UPLOAD_BYTES`, `NEXT_PUBLIC_SITE_URL`) come from `.env.local`
too, so the tests expect what the running site was built with. Restart the site after changing them.

Nothing is skipped by the environment: a missing precondition fails the test. The site is built
without `NEXT_PUBLIC_MAINTENANCE` (`/maintenance` must be a 404) and without `VERCEL` (every page is
noindex, robots.txt disallows all), Client-API supports UA, PL, DE and FR (EUR among the currencies)
and has loaded its disposable-domain list, and the home page has notices in both rails.

## Tags

Set with Playwright's `tag` option: `test("…", { tag: "@responsive" }, …)`.

| Tag | Effect |
|---|---|
| (none) | desktop only |
| `@responsive` | desktop, mobile and tablet: the behaviour depends on the viewport |
| `@mobile-only`, `@tablet-only` | that viewport only (for what exists only there, e.g. the bottom tabs) |
| `@layout-sweep` | runs only in the `layout-sweep` project (it resizes the window itself, minutes) |

A `@responsive` test asserts one behaviour; when the expected value depends on the viewport (the
dialog's shape, the preview's place), it is computed from the breakpoints in `support/viewport.ts`. Two
different checks for two viewports are two tests (`@mobile-only` next to a desktop one).

## Test data

The database is shared with real data and nothing is ever deleted, so there are two kinds of data.

### The shared dataset (`support/shared.ts`, fixture `shared`)

Tests whose point is **not** creating data (navigation, a11y, layout, item pages, lists, filters,
sorting, pagination, SEO) read one shared set of notices instead of creating their own.

- **Marker** `e2eshared<yy><ww>`: the ISO week-numbering year and week in Europe/Kyiv, e.g.
  `e2eshared2641`, plus `E2E_SHARED_SUFFIX`. A new set appears every week, so its notices stay young
  enough (a "fresh" one is at most ~8 days old, the stale one ~26) and the API's `@WithinDays(30)`
  holds when a missing one is re-created.
- **Groups** are found with `?search=<marker>-<group>` (`shared.search.<group>`); no group name is a
  prefix of another. Titles are `<marker>-<group>-<key> <Ukrainian name>`, places
  `e2e-<marker>-<city>` named `<marker> <city>`.
- **Seeding** happens once per run in `global-setup.ts`: every group is looked up through
  `lostItems`/`foundItems`, only the missing titles are created, and the result is written to
  `e2e/.state/shared.json`, which the worker fixture `shared` reads. A notice that disappears (the
  Worker archives a claim target a week after its last claim) is simply created again. When a stored
  notice no longer matches its definition (someone changed `shared.ts` mid-week), duplicates exist or a
  group holds an unknown title, the setup fails and asks for a fresh set.
- **Dates** are offsets from the set's anchor day (`shared.anchor`, the day it was first seeded), not
  from today. Read `item.input.date`; never assume "today minus n".
- **Old sets just stay** in the database. To start a fresh set (for example after changing a
  definition), run with `E2E_SHARED_SUFFIX=b` (lowercase letters/digits, up to 6).
- Never confirm a return on a shared notice (`confirmReturn` archives it) and never claim the `view`
  notices; claims go to `shared.items.claimLost` / `claimFound`.

### Own rows (`support/data.ts`, fixtures `data` and `token`)

Tests whose point **is** creating data (the report form, claims) create it through the API or the UI:

- `global-setup.ts` picks a `RUN_ID` (`e2e<yyMMddHHmm><3 chars>`, also written to
  `e2e/.state/run.json`).
- Each test gets a `token` (`<RUN_ID>-<worker><n>`). The `data` fixture puts it in front of every
  title, in place ids (`e2e-<token>-<n>`) and names, and in contact emails
  (`<role>-<token>-<n>@e2e.derechi.test`). Find your rows with `?search=<token>`.
- These rows stay too; photos live under `items/e2e/` in MinIO.

## Writing tests

**Every Web-Client feature or change comes with e2e tests.** A new page, form, filter or other
visible behaviour gets a test in `specs/`; a changed behaviour updates the tests that cover it
(find them in `TESTS.md`); a removed feature takes its tests with it. Update `TESTS.md` in the same
change. Tag a test `@responsive` only if it depends on the viewport; everything else runs on desktop.

- Import `test` and `expect` from `e2e/fixtures/base.ts`. Fixtures: `appLocale`, `t` (the app's
  bundles, keys checked by `tsc`), `shared`, `token`, `data`, `flags`, `go(path)`, `consoleErrors`.
- Every test runs the base guard: the BotID script is stubbed, Google Maps is blocked (the place
  picker falls back to the search over known places: use `shared.place`), and any `console.error` or
  page error fails the test unless it is in `allowedConsoleErrors` (`support/env.ts`) or allowed by
  the test with `consoleErrors.allow(pattern, reason)`. Requests to hosts other than the site, the API
  and the files host are listed in the test's annotations.
- Locate by role and label with texts from `t(...)`; never hard-code a language. Page objects in
  `fixtures/pages/` hide the differences between desktop, mobile and tablet.
- Wait for URLs, roles, `data-state`, `aria-busy` or `expect.poll`, never `waitForTimeout`. `go()`
  waits for hydration; after `page.goto`, `page.reload` or a client-side navigation that is used at
  once, call `waitForHydration` (`support/hydration.ts`), the only place that looks at React internals.
- Expected dates, money and country names are formatted in the browser (`support/browser-intl.ts`),
  never in Node. Prefer values the page or the API already has (a card's `<time dateTime>`, the masked
  contacts and the coordinates the API returns) over re-implementing the app's logic.
- A test asserts one expected behaviour: no `if`/`??` that lets it pass either way.

Where things live: `support/` holds the data (`api`, `data`, `shared`, `minio`), the environment
(`env`), the bundles (`i18n`) and helpers by subject (`site` URLs and cookies, `seo`, `hosts`,
`viewport`, `hydration`, `browser-intl`, `key-pages`, `legal`, `claims`); `fixtures/pages/` the page
objects.
- Type-check and lint: `pnpm exec tsc -p e2e/tsconfig.json --noEmit`, `pnpm exec biome check e2e`.
