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

## Feature flags

Both are `NEXT_PUBLIC_` variables (`src/lib/env/client.ts`), so they are inlined at build
time: flipping one means a rebuild and redeploy of the web client, not a restart.

| Variable | Default | Effect |
|---|---|---|
| `NEXT_PUBLIC_SMS_OUTAGE` | `true` | The claim form shows a one-time warning toast that SMS is down and emails arrive as usual. Nothing is blocked. Turn it on when SMS-fly is unavailable or `SMS_FLY_API_KEY` is empty in `Notification`. |
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
