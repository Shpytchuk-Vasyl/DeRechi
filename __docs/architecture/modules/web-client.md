# Web-Client

The public website: a Next.js (App Router) application in `Web-Client/`, built and run with
pnpm. It is a sibling of the Maven reactor, not a module of it, and has its own
`README.md`, `CLAUDE.md` and `AGENTS.md` with the details. This page records only what a
backend developer needs to know.

| | |
|---|---|
| Port | 3000 (`pnpm dev` / `pnpm start`) |
| Talks to | the API entry on 8080 (Caddy for now): GraphQL at `GRAPHQL_URL` from the server side, images from `NEXT_PUBLIC_FILES_URL` (`/files/**`), uploads by presigned PUT to `/derechi-files/**` |
| Stack | Next.js, React, Tailwind v4, shadcn/ui, GraphQL code generation, Biome, Vitest |

## How it talks to the backend

Server components and server actions call `Client-API` through the API entry, which is
[Caddy](../../deployment/infrastructure.md#caddy-api-entry) for now; the browser itself never
sends a GraphQL request, apart from the "show more" server action. Images are loaded
straight from Caddy's `/files/**` route, which is why that origin has to be in
`next.config.ts`'s `remotePatterns`.

Photo uploads go from the browser to MinIO through Caddy's upload route with a URL the
server action signed (in production straight to the Backblaze B2 bucket, `S3_PUBLIC_ENDPOINT`); `Client-API` only ever receives the object key. The full
sequence, the environment variables and how to tell a CORS refusal from a bad signature are
in `Web-Client/README.md`; the entry side is in [../file-storage.md](../file-storage.md).

## What a backend change means here

- **GraphQL schema changed** (`Client-API/src/main/resources/graphql/schema.graphqls`): run
  `pnpm codegen` in `Web-Client`. Code generation reads the schema file directly plus
  `Web-Client/graphql/connections.graphqls` for the Relay connection types that Spring adds
  at runtime, so no server has to be running, and the generated diff shows exactly what the
  front end has to adapt to. See
  [../../extending/add-a-graphql-field.md](../../extending/add-a-graphql-field.md).
- **New locale**: the web client ships the same five locales as the admin panel (`en`,
  `uk`, `pl`, `de`, `fr`), shares the `DERECHI_LOCALE` cookie with it, and puts the locale
  in every URL (`/uk/lost`). Copy lives in `Web-Client/messages/*.json`, and `pnpm test`
  fails on a key or argument present in one bundle and not the others, like `MessagesTest`
  does for the admin panel.
- **Claims**: the notice page posts `claimLostItem` / `claimFoundItem` from a server action and
  `/{locale}/claims/{token}` is the page behind the reminder links (`confirmReturn`); the
  backend's `DERECHI_SITE_URL` must point at this client for those links to work.
- **New query**: every GraphQL read in `src/api/` takes its TTL and cache tag from
  `src/lib/cache.ts` (`CACHE`), and server actions drop tags through `invalidate` there.
  Pick a TTL tier by how stale the data may get and add a policy rather than a number at the
  call site. `stats` is cached for five minutes and refreshed when a found notice is posted.
- **New country or currency**: the client reads `countries { code currency }` from the
  API; nothing is hard-coded on its side.
- **Entry CORS or routes**: Caddy adds no CORS headers. The presigned upload is the only
  cross-origin request, and the storage answers it: MinIO allows any origin by default, so a
  LAN address works when testing from a phone; in production the B2 bucket's CORS rules must
  list the site. `WEB_ORIGIN_PATTERNS` belongs to the `Getaway` module and is read by
  nothing while it is not deployed.
- **Archive age** (`derechi.archive.max-age` in `Worker`, 30 days): `STALE_AFTER_DAYS` in
  `src/lib/seo.ts` is the same number. A notice that old answers `noindex` and leaves the
  sitemap, and `Worker` archives it on its next run; change both together.
- **Notice titles** (the web form or `Ingest`): the page title and the share card print the title
  as it is, with no lost/found prefix or badge, because titles from `Ingest` already start with
  the verb. A title without one ("Синій рюкзак") does not say on those whether it was lost or found.
- **Photo formats**: the share card draws only JPEG and PNG photos; a notice whose photo is WebP or
  GIF gets the card without it.

## Search

How search engines see the site; the details are in the "Search" section of `Web-Client/README.md`.

- **One language per notice.** A notice's text is the same in all five locales, so only its `/uk/`
  page is indexed (`ITEM_LOCALE` in `src/lib/seo.ts`): it has a self `canonical` and no `hreflang`,
  the other four answer `noindex, follow`, and the sitemap lists notices by `/uk/` URL alone. The
  home, list and legal pages stay indexed in every locale with `hreflang`.
- **Sitemap.** `robots.txt` lists `pages.xml` and one file per kind and calendar month
  (`lost-0`, `found-1`, …). A month without notices lists its kind's list page, because Search
  Console rejects a sitemap without a `<url>`.
- **Home.** The `<title>` is `app.homeTitle` ("DeRechi · Загублені та знайдені речі" and its
  translations). The `WebSite` JSON-LD carries `alternateName` ("Де речі", "derechi.site") and a
  `publisher` with the logo and `sameAs` the Telegram channel (`t.me/derechi_ua`): if the channel
  moves, update `src/app/[locale]/page.tsx`.
- **City pages.** `/{locale}/{city}` for Kyiv and the regional centres (`src/lib/cities.ts`, a centre
  and a radius each) lists the found and lost notices within the radius through the `near`
  filter, so the backend has no notion of a city. A city page is the home page without the map
  (its own file, so the two can diverge), with the hero, the search and the rails about the city.
  The home page links the cities from a static map of Ukraine (Natural Earth outline in Ukraine's
  point of view, Crimea included), a notice page links the city its coordinates fall into, and that city is added after the place in the
  notice's `<title>` ("Привокзальний ринок, Львів"). A city without live notices answers `noindex` and is left out of the sitemap.
- **Share cards.** Every notice page has its own `opengraph-image` (1200×630: photo, title, place,
  date), drawn with Nunito loaded from Google Fonts; the other pages share one card per locale.

## Feature flags

Both are `NEXT_PUBLIC_` variables (`src/lib/env/client.ts`), so they are inlined at build
time: flipping one means a rebuild and redeploy of the web client, not a restart.

| Variable | Default | Effect |
|---|---|---|
| `NEXT_PUBLIC_SMS_OUTAGE` | `true` | The claim form shows a one-time warning toast that SMS is down and emails arrive as usual. Nothing is blocked. Turn it on when SMS-fly is unavailable or `SMS_FLY_API_KEY` is empty in `Notification`. |
| `NEXT_PUBLIC_TALLY_FORM` | empty | The feedback form on Tally: one form id (`wA1b2C`) for every language, or `uk=wA1b2C,en=xY9z8W` (the visitor's language, else `uk`, else the first). Empty hides the feedback icon in the header and the footer link. See [Feedback](#feedback). |
| `NEXT_PUBLIC_MAINTENANCE` | `false` | Technical work. `src/proxy.ts` rewrites every page except the home page, `/terms` and `/privacy` to the `/{locale}/maintenance` route handler, so the closed URL answers 503 with `Retry-After: 3600` and search engines keep it indexed (Google's advice for planned downtime). `/api/*` (the Fourthwall webhook among them) and files stay outside the proxy, so they keep working. With the flag off the route is a 404. |

Maintenance mode only closes the web client. The admin panel and the backend stay up, and
the reminder links (`/{locale}/claims/{token}`) lead to the maintenance page too while it is on.

## Commands

```bash
cd Web-Client
pnpm install
pnpm dev          # http://localhost:3000, needs Caddy (docker compose up -d) + Client-API running
pnpm codegen      # after a schema change
pnpm lint         # Biome
pnpm typecheck
pnpm test         # Vitest: bundle parity, schemas, server actions, URL/SEO helpers; upload tests run only with S3_TEST_ENDPOINT set
```

`pnpm test` runs offline: besides bundle parity it covers the report and claim schemas, the
`createNotice` / `claimNotice` / `confirmReturn` server actions with the GraphQL client, bot
check, cookies and cache mocked (validation, the `DERECHI_CLAIM_<kind>_<id>` cookie, `NOT_FOUND`
handling, revalidated tags), and the search, date and SEO helpers. React components are not
unit-tested. The list of test files is in `Web-Client/README.md`.

The web client is not part of the Docker Compose setup or the `Dockerfile`; it runs with
pnpm on the developer machine or wherever Next.js is deployed.

## Feedback

Feedback goes to a [Tally](https://tally.so) form, not to our backend: replies come by Tally's email notifications, and nothing is stored in our database.

- The icon next to the language switcher (`FeedbackButton`) is a link to `https://tally.so/r/<form>?page=…`, the form of the visitor's language. A plain click loads Tally's `embed.js` on demand and opens the form as a modal popup (`Tally.openPopup`, hidden field `page`; as wide as the screen up to 600 px, as Tally takes the width in pixels only and itself keeps the popup inside a narrow screen), so no third-party script runs before the visitor asks for it; a middle or modified click opens the form page in a new tab. When the script does not load (Tally is down or blocked), nothing throws: the visitor gets a warning toast that the form is unavailable (`nav.feedbackUnavailable`) and stays on the page; the next click tries again.
- `page` is the path without the locale, and a claim page loses its token (`/claims/<token>` becomes `/claims`), as the token confirms a return. Tally's popup would also send the full path as `originPage` (and every query parameter of the page): the site passes `originPage` with the same safe value, which overrides it.
- The scrollbar is ours, not Tally's: a form taller than the screen would scroll inside Tally's iframe, with a grey scrollbar our styles cannot reach (another origin). `globals.css` lifts Tally's cap on the iframe (`max-height: none`), so it takes the form's full height, and caps Tally's own container instead (the screen minus 40 px, the margin Tally keeps at the sides). That container already has `overflow-y: auto` and lives in our page, so it scrolls with the site's lavender scrollbar. The close button is inside the form and scrolls away with it; a click on the dimmed page closes the popup too. The selectors (`.tally-overlay .tally-popup iframe`, `.tally-overlay .tally-popup > div`) rely on the class names and markup Tally's embed adds to our page: if an update changes them, the rules stop matching and the popup goes back to Tally's own scrolling.
- The footer has a Contact column with the same form as a plain link in a new tab (`page=footer`).
- The forms themselves live in Tally, one per language: how to build them is in [Feedback forms](../../processes/feedback-forms.md).
