# Web-Client

The public website: a Next.js (App Router) application in `Web-Client/`, built and run with
pnpm. It is a sibling of the Maven reactor, not a module of it, and has its own
`README.md`, `CLAUDE.md` and `AGENTS.md` with the details. This page records only what a
backend developer needs to know.

| | |
|---|---|
| Port | 3000 (`pnpm dev` / `pnpm start`) |
| Talks to | `Getaway`: GraphQL at `GRAPHQL_URL` from the server side, images from `NEXT_PUBLIC_FILES_URL` (`/files/**`), uploads by presigned PUT to `/derechi-files/**` |
| Stack | Next.js, React, Tailwind v4, shadcn/ui, GraphQL code generation, Biome, Vitest |

## How it talks to the backend

Server components and server actions call `Client-API` through the gateway; the browser
itself never sends a GraphQL request, apart from the "show more" server action. Images are
loaded straight from the gateway's `/files/**` route, which is why that origin has to be in
`next.config.ts`'s `remotePatterns`.

Photo uploads go from the browser to MinIO through the gateway's `files-upload` route with
a URL the server action signed; `Client-API` only ever receives the object key. The full
sequence, the environment variables and how to tell a CORS refusal from a bad signature are
in `Web-Client/README.md`; the gateway side is in [../file-storage.md](../file-storage.md).

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
- **New country or currency**: the client reads `countries { code currency }` from the
  API; nothing is hard-coded on its side.
- **Gateway CORS or routes**: `WEB_ORIGIN_PATTERNS` must include the origin the browser
  uses, including a LAN address when testing from a phone.

## Commands

```bash
cd Web-Client
pnpm install
pnpm dev          # http://localhost:3000, needs Getaway + Client-API running
pnpm codegen      # after a schema change
pnpm lint         # Biome
pnpm typecheck
pnpm test         # bundle parity; upload tests run only with S3_TEST_ENDPOINT set
```

The web client is not part of the Docker Compose setup or the `Dockerfile`; it runs with
pnpm on the developer machine or wherever Next.js is deployed.
