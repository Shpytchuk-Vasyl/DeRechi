# Web-Client

Public web client for DeRechi. Next.js App Router with server rendering, GraphQL against
`Client-API`, five locales, Tailwind v4 and shadcn/ui.

Not a Maven module: it lives beside the reactor and is built with pnpm.

## Commands

```bash
pnpm install
pnpm dev          # http://localhost:3000
pnpm build        # needs Client-API reachable at GRAPHQL_URL
pnpm start

pnpm codegen      # regenerate types from Client-API's schema file
pnpm lint         # Biome (format + lint)
pnpm format       # Biome with --write
pnpm typecheck
pnpm test         # Vitest: bundle parity, schemas, server actions, URL/SEO helpers
```

## How requests flow

The browser never talks to the Gateway. Server components and server actions call
`GRAPHQL_URL` from the server, and the only query surface exposed to the client is the
`loadMoreItems` server action behind the "show more" button. The one exception is images:
they are served straight from `NEXT_PUBLIC_FILES_URL` (the Gateway's `/files/**` route into
MinIO), which is why that origin is in `next.config.ts`'s `remotePatterns`.

## Types

`pnpm codegen` reads `../Client-API/src/main/resources/graphql/schema.graphqls` plus
`graphql/connections.graphqls`; the latter spells out the Relay connection types that
Spring for GraphQL registers at runtime and therefore keeps out of the schema file. No
running server is needed, so a schema change shows up as a diff here.

## Caching

Every GraphQL read in `src/api/` is a `fetch` cached by Next, and all the numbers live in
`src/lib/cache.ts`:

- `CACHE_TTL`: four tiers, `minute` (lists, place suggestions), `fiveMinutes` (home page
  `stats`), `hour` (one notice, categories, countries) and `day` (the sitemap, OG fonts).
  Pick the tier by how stale the data may get, not per call site.
- `CACHE_TAG` and `CACHE`: the tag and the policy (`{ revalidate, tags }`) of each read,
  passed straight to `graphqlRequest`.
- `invalidate(...tags)`: what server actions call after a write; it is `revalidateTag(tag, "max")`,
  so the next visitor still gets the old response while the fresh one loads.
- `NO_STORE`: mutations and per-visitor reads (claim status).

Two things Next does not make obvious. A fetch with a shorter `revalidate` shortens the whole
route, which is why the sitemap reads the lists through `CACHE.sitemapItems` (a day) and not
`CACHE.items` (a minute). And route segment config such as `export const revalidate` has to be
a literal, so `sitemap.ts` repeats `86400` with a comment instead of importing `CACHE_TTL.day`.

## Tests

Vitest, colocated as `<name>.test.ts`, no browser and no running backend:

- `i18n/messages.test.ts`: the five bundles have the same keys and arguments.
- `schema/report-schema.test.ts`, `schema/claim-schema.test.ts`: the client rules mirror
  Client-API's inputs (E.164 phone, email width, photo on a found notice, date window) and
  the mapping to `ItemInput` / `ContactInfoInput` sends empty optionals as `null`.
- `app/actions/report.test.ts`, `app/actions/claim.test.ts`: the server actions with
  `@/graphql/client`, `@/lib/bot-check`, `next/headers` and `next/cache` mocked: invalid
  input and bots never reach the API, the mutation variables, the 30-day
  `DERECHI_CLAIM_<kind>_<id>` cookie holding the claim id, `NOT_FOUND` versus other
  failures, which cache tags are revalidated, and `claimStatus` refusing a malformed id.
- `lib/claim-cookie.test.ts`, `lib/item-search.test.ts` (query string to filter and back),
  `lib/intl/country.test.ts`, `lib/intl/dates.test.ts`, `lib/seo.test.ts`.
- `lib/uploads/presign.test.ts` and `presign.integration.test.ts`, see "Uploads" below.

React components are not unit-tested; `async` server components are not supported by
Vitest, and the screens are checked by hand in `pnpm dev`.

## Errors

Server errors (server components, server actions, route handlers, the proxy) are logged by
`onRequestError` in `src/instrumentation.ts` as one JSON line each (`"event":"request_error"`, the
message, digest, stack, method, path without the query string, route), so the hosting's log view
can filter them. Request headers and the query string stay out: they carry cookies, claim ids and
search terms. Errors in the browser are not collected.

## Locales

`en` (fallback), `uk`, `pl`, `de`, `fr`: the same five the admin panel ships, sharing its
`DERECHI_LOCALE` cookie. Every URL carries its locale (`/uk/lost`). Copy lives in
`messages/*.json`; `pnpm test` fails when a key or a message argument exists in one bundle
and not in the others, which is the only way that divergence is visible before it reaches
the screen.

## Countries and currencies

Country is a second axis next to the language. The locale lives in the URL and
`DERECHI_LOCALE`; the country in `DERECHI_COUNTRY`, with no URL segment: a Pole in Germany
reads Polish copy, sees euro rewards and the German terms.

The list of supported countries, and with it the reward currencies, comes from
`Client-API`'s `countries` query (`fetchCountries`, cached an hour; during an outage it falls
back to the API's default country so the layout still renders) and is hardcoded nowhere
here; country names come from `Intl.DisplayNames`, so a new country needs no message key.
`CountryProvider` in the locale layout hands the list to the client together with the viewer's
country, which it reads **from the cookie on the client** (`useSyncExternalStore`, server
snapshot = the first country in the list). Reading `cookies()` in the layout would make every
page dynamic; this way the home and list pages stay cacheable and only the client corrects
the currency of the demos on hydration.

The viewer never chooses the country; there is no switcher. The proxy seeds the cookie from
Vercel's `x-vercel-ip-country` on the first visit, which is the one thing here that depends on
the hosting: on any other host the header is absent, the cookie is never written and the
fallback country applies to everyone. The same holds in `next dev`. The only override is the
`?country=XX` query on `/terms` and `/privacy`, which the notice form uses to open the legal
texts for the country of the place being reported.

A notice carries the country of its **place**: the autocomplete reads it from Google's
`address_components`, the geolocation path from the geocoder result, known places come back
from the API with it, and a place typed by hand takes the viewer's country. `componentRestrictions`
now covers all supported countries (Google allows at most five). The reward is `MoneyInput`:
the amount plus an optional currency; the select in the contacts step shows the currency of
the place's country and only stores an explicit choice, the same default `Client-API` applies.
Rewards are displayed in the currency they were posted in (`formatMoney`); nothing converts.

## Claims

A viewer who recognises a notice ("it's mine" on a found one, "I found it" on a lost one)
answers it from the notice page itself: `ClaimCard` sits where the masked contacts are and
expands into an inline form (phone, email, messengers: the same `ContactFields` the report
form uses). The detail page also renders inside the modal route's `RouteDialog`, which is why
the form is inline and not a nested dialog; the small unlock dialog is the one exception. Nothing the claimant types is shown anywhere;
`Client-API` stores it and Worker mails and texts it to the notice's author.

`claimNotice(kind, id, values)` in `src/app/actions/claim.ts` validates with `claimSchema`,
runs the same BotID check as `createNotice` (`src/lib/bot-check.ts`; the claim posts to the
notice page, so `/*/lost/*` and `/*/found/*` are in `instrumentation-client.ts`'s `protect`
list), calls `claimLostItem`/`claimFoundItem` and sets `DERECHI_CLAIM_<kind>_<id>` to the
claim's token (a UUID, `CLAIM_TOKEN` in `claim-schema.ts`) for 30 days. The cookie is readable
by script on purpose: the page stays cacheable, the server render always shows the button, and
`ClaimCard` swaps in the "already sent" state on mount. A second claim with the same phone or
email comes back as `repeated: true`, and nothing is sent again.

BotID's client starts only in a secure context (`window.isSecureContext`): its challenge needs
`crypto.subtle`, which plain `http` on a LAN IP does not have, so testing `next dev` from a phone at
`http://192.168.x.x:3000` used to hang every protected request with "Cannot read properties of
undefined (reading 'importKey')". Production is `https`, and off Vercel the server check passes
everyone anyway, so nothing is lost.

The author and later the claimant get a reminder linking to `/<locale>/claims/<token>`. That
page (`noindex`, disallowed in `robots.txt`) has one button that calls `confirmReturn(token)`;
the notice is then archived by Worker. An unknown or used-up token reads as "this
link is no longer valid". The action revalidates both list tags, but not the notice's own
detail tag: the token does not say which notice it closes, so that page stays cached until its
hour runs out, and a claim sent from it in the meantime answers `notFound`, which revalidates
that notice's tag.

### SMS outage notice

`NEXT_PUBLIC_SMS_OUTAGE=true` is a feature flag for when the SMS channel is down: opening the
claim form and opening the unlock dialog each show a warning toast once ("SMS is temporarily
unavailable, emails arrive as usual", `smsOutage.*` in the bundles, `useSmsOutageNotice`).
Nothing is blocked; the claim and the payment go through as usual. Unset or `false` turns it
off. Being `NEXT_PUBLIC_`, it is inlined at build time, so flipping it needs a rebuild.

### Maintenance mode

`NEXT_PUBLIC_MAINTENANCE=true` closes the site for technical work: `src/proxy.ts` rewrites every
page except the home page, `/terms` and `/privacy` to `/{locale}/maintenance`, so the closed URL
itself answers **503** with `Retry-After: 3600` and crawlers keep it in the index. The maintenance
"page" is a route handler (`src/app/[locale]/maintenance/route.ts`) that returns standalone HTML
with inline styles and `noindex` (`maintenance.*` in the bundles): an App Router page cannot answer
503, and a rewrite to a page keeps its 200. Links on the home page still lead there; a client-side
navigation gets HTML instead of an RSC payload and falls back to a full page load. Non-GET requests
to a closed page get 405. `/api/*` and files are outside the proxy matcher and keep working. With
the flag off the route is a 404.
Same as the SMS flag, it is inlined at build time and needs a rebuild.

### Unlocking the author's phone number (Fourthwall)

After a claim, the "done" state offers the author's phone number (only the number, not the
email) for about $1 paid on Fourthwall (`ClaimUnlock` in
`src/screens/found_lost/claim/claim-unlock.tsx`). Nothing on the site ever shows the number: the
Fourthwall webhook marks the claim paid, and Worker sends the number to the claimant by email
and SMS.

- Opening the dialog calls `unlockClaim(kind, itemId, claimId)` (`src/app/actions/claim.ts`, behind the same
  BotID check as the claim itself). Client-API creates a hidden digital product on Fourthwall
  for this claim and answers with its `checkoutUrl`
  (`https://derechi-shop.fourthwall.com/cart/checkout?products=<variantId>:1`); the dialog
  shows it as a "Continue to payment" link that opens in a new tab, where Apple Pay, Google Pay
  and cards are available. A `PAYMENT_UNAVAILABLE` error (Fourthwall rate-limits product
  creation) reads as "try again in a minute" with a retry button; `UNLOCK_LIMIT` (one checkout
  per claimant a week, by phone or email) shows the date from `extensions.retryAfter` and no
  retry button; any other failure gets a generic retry. There is no code to type and no environment variable on this side.
- Above the payment link sits a required waiver checkbox (`claim.unlock.waiver`): the buyer
  expressly agrees that the number is sent right after payment and acknowledges losing the right
  of withdrawal, which EU law (PL, DE, FR) asks for before payment for digital content. Both
  elements stay in the label itself, because a general "I accept the terms" does not count as
  that consent; the details (the 14 days, the statute per country, what happens if the number
  does not arrive) are in the terms, section 5, linked from the label with `#author-number`. Clicking
  "Continue to payment" unticked does not open the checkout: the box is a small react-hook-form
  form (`waiverSchema` in `claim-schema.ts`, field `consent`), and submitting it shows
  `form.error.waiver` under the box, marks it invalid and focuses it. Until the tick the button is
  a submit button with no `href`, so a middle click or "open in new tab" cannot skip it either. The tick lives only in the dialog's state, is
  cleared every time the dialog opens and never reaches the server. Creating the checkout on open
  is not a payment, so it does not wait for the tick. Paid claims show no checkbox.
- The claim cookie holds the claim id, for 30 days, so a returning claimant sees the button
  and how far the unlock got (`PhoneUnlock`); `useClaim` reads it with `readClaimCookie` and asks
  `claimStatus(kind, itemId, claimId)` on mount, which also returns the `checkoutUrl` once it
  exists. The claim token never reaches the site: it lives only in the reminder links for
  `confirmReturn`. Old cookies holding `1`, a `DR-` payment code or a token still mean "already
  responded" and simply show no unlock block.
- While the dialog is open, it polls `claimStatus` every 20 s until the number is sent, and
  gives up after 15 minutes; reopening the dialog starts a new window. The webhook is the only
  source of truth, so there is no "I paid" button.

- Paying for real is not needed in development: `/<locale>/playground` has a "Fourthwall" bench
  that posts a fake `ORDER_PLACED` for the variant from a checkout URL to Client-API's webhook,
  signed with `FOURTHWALL_WEBHOOK_SECRET` (must equal Client-API's
  `derechi.fourthwall.webhook-secret`, default `dev-secret`). Everything for it lives in
  `src/app/[locale]/playground/` (the bench, the `sendFakePayment` action, the signing helpers and
  their test); the playground folder is the home of such local test tools, nothing of it goes
  elsewhere in the project. The bench can also read the claim token from the `DERECHI_CLAIM_*`
  cookie and show `claimStatus` for it. The page and the action refuse in production.

## Legal texts per country

`src/content/legal/<locale>.json` holds the documents; the sentences that depend on the
country are slots: `{findersLaw}` (finder's duties and fee), `{governingLaw}`, `{dataLaw}`,
`{rightsBasis}`, `{complaintRight}` and `{withdrawalLaw}` (the consumer law under which the
right of withdrawal ends for the author's number: the statute and article in PL, DE, FR; only the
law's name for UA, whose new consumer law with the digital content article is not in force yet).
They are filled from `src/content/legal/jurisdictions/<CC>.json`, one file per supported country
with the six sentences in every locale and that country's `updated` date. Adding a country to
`derechi.countries.supported` therefore means adding one jurisdiction file; without it the
page falls back to the first jurisdiction and warns in the server log. A section may carry an
`id`, rendered as the `<section>`'s anchor: the terms' section 5 is `author-number`, which the
unlock dialog's waiver links to.

`/terms` and `/privacy` read the viewer's country (cookie, then geo header) on the server, so
only those two pages are dynamic. `?country=XX` overrides it: the consent checkbox in the notice
form links to the version for the country of the place being reported, since that is the
market the notice is posted in.

Both the notice form and the claim form end with a required consent checkbox
(`ConsentCheckbox`, field `consent`, error `form.error.consent`): nothing is published or sent
until it is ticked. It exists only in the client-side schemas (`draftSchema`, `claimFormSchema`);
the server actions validate with `reportSchema`/`claimSchema`, which strip it, so Client-API
never sees it. A restored report draft never brings the tick back: consent is given anew.
`ConsentCheckbox` is only the react-hook-form binding: the label with links and the field error
are the pouf `Checkbox`'s own (`label` takes rich content, `error` renders the standard
`FieldError`). The unlock dialog's withdrawal waiver uses the same `ConsentCheckbox`. The texts are drafts for a lawyer; the operator and contact
email they name come from `LEGAL_CONTACT` in `src/content/legal/types.ts`. The privacy policy
still carries two placeholders, `[EMAIL AND SMS PROVIDER, HOSTING: TO BE ADDED]` and
`[SIMILAR FINDS NOTIFICATIONS: TO BE DESCRIBED]`, to be written once those are settled.

## Uploads

Photos go **straight from the browser to MinIO through the Gateway**; no image byte passes
through Node, and `Client-API` needs no upload endpoint.

The file is held in the browser while the form is filled in and travels **when the notice is
submitted**, not when it is chosen: an abandoned form then leaves nothing in the bucket, and
the photo is only stored once the rest of the notice has passed validation.

1. `createUploadTicket` (a server action) checks the type and size, generates the key
   (`items/yyyy/MM/<uuid>.<ext>`, the layout `Admin-API` already writes) and signs a PUT URL
   with SigV4, see `src/lib/uploads/presign.ts`. It signs with `S3_ACCESS_KEY`/`S3_SECRET_KEY`,
   MinIO's application account (`derechi-app` locally, created by `minio-init`), which may only
   read, write and delete objects in the bucket; never the root credentials.
2. The browser PUTs the file to `http://<gateway>/derechi-files/<key>?X-Amz-…`.
3. The Gateway's `files-upload` route forwards it to MinIO with **`PreserveHostHeader`** and
   no path rewrite: the signature covers both, so either one would break it. A presigned PUT
   cannot cap its own body, so a `RequestSize` filter does (5 MB, `MINIO_MAX_UPLOAD`).
4. The notice stores the key; reads go through the existing `/files/**` route.

Because the key only exists after the upload, the browser validates the form with
`draftSchema` (the full schema minus `image`) and the "a found item needs a photo" rule is
checked against the picked file. The server action still runs the complete `reportSchema`, so
the contract with Client-API is unchanged.

`S3_PUBLIC_ENDPOINT` must therefore be the origin **the browser** uses (the Gateway), not
MinIO's internal address: it is signed into the URL. Testing from a phone on the same
network means the LAN address (`http://192.168.x.y:8080`), and the Gateway's
`WEB_ORIGIN_PATTERNS` has to let that origin through CORS.

A `403` on the PUT with an **empty body and no `Server: MinIO` header** is the Gateway
refusing the request on CORS grounds, not a bad signature: MinIO answers with an XML body and
its own `Server` header. The browser's `Origin` has to match `WEB_ORIGIN_PATTERNS`, which is
what makes uploads from a phone on the LAN work.

A `404` on the PUT comes from the Gateway, not from MinIO. MinIO would answer `403` for a
bad signature and `404` only for a missing bucket. It means the `files-upload` route is not
in the running Gateway. `curl -i -X PUT http://localhost:8080/derechi-files/probe` tells the
two apart: `403` means the route is live and MinIO rejected the unsigned request, `404` means
the Gateway is still running an older config.

The signing is verified against a real MinIO rather than a golden string:

```bash
docker compose up -d minio                   # from the repo root
./mvnw -pl Getaway spring-boot:run           # for the through-the-Gateway run

S3_TEST_ENDPOINT=http://localhost:9000 pnpm test                      # MinIO directly
# through the Gateway (adds the 413 size-cap check)
S3_TEST_ENDPOINT=http://localhost:8080 S3_TEST_READ_URL=http://localhost:8080/files S3_TEST_MAX_BYTES=5242880 pnpm test
```

Without `S3_TEST_ENDPOINT` those tests skip, so `pnpm test` stays offline by default.

## Tour

driver.js, mounted per page with `<Tour id="home" />`. Steps point at `data-tour` attributes
rather than class names, and a step whose element is not on the current screen is dropped
instead of being shown against a page corner.

It runs itself once per visitor (`derechi.tour.<id>` in localStorage, wrapped in try/catch
because private windows throw) and afterwards only from the header button. That button comes
from `TourProvider`: a page with a tour registers its run function, and the button renders
only when one has, so it never offers a tour that is not there.

driver.js substitutes its own `{{current}}`/`{{total}}` in the progress text, which ICU would
refuse to parse, so the bundle keeps plain `{current}`/`{total}` arguments and the component
passes driver's tokens in as values.
## Search

Every page sets its own `canonical` and one `hreflang` per locale (plus `x-default`) through
`pageAlternates` in `src/lib/seo.ts`; the layout's alternates alone would point every page at
the locale's home. Notice pages get a title of the form `Lost: Keys – Lviv, 12 Sept 2026`,
breadcrumbs and an `ItemPage` in JSON-LD; the home page adds `WebSite` with a `SearchAction`.
JSON-LD goes into the page only through `jsonLd()` in `src/lib/seo.ts`: `JSON.stringify` leaves `<`
as is, so a notice titled `</script><script>…` would otherwise run as a script on its own page.
Pages without a photo inherit the generated card from `[locale]/opengraph-image.tsx`.

Nothing is indexable unless `NODE_ENV` is `production` **and** `NEXT_PUBLIC_SITE_URL` is a public
host (`INDEXABLE` in `src/lib/seo.ts`): `next dev`, a local `pnpm start` and a LAN address all
answer `noindex` on every page and `Disallow: /` in `robots.txt`, so a tunnel or a shared link
never puts `localhost:3000` into an index.

A notice older than `STALE_AFTER_DAYS` answers `noindex, follow` and drops out of the sitemap:
the API has no status yet, so age stands in for "resolved". The sitemap is split with
`generateSitemaps` into `pages` plus one chunk per kind and calendar month (`lost-0`,
`found-1`, …), each a date window walked with the cursor, and `robots.txt` lists all of them.

The list pages accept `?after=<cursor>`: the page is rendered from that cursor and its
metadata carries `<link rel="next">` to the following one, so a crawler can walk past the
first page without the "show more" button.

### TODO

- `lastModified` in the sitemap is the notice's own date, not when the record last changed:
  `Client-API` exposes no `updatedAt`, so search engines see every notice as never updated.
- `changeFrequency` on the list pages is ignored by Google; it stays only for other crawlers.
- Replace the age-based `noindex` with a real status (returned / archived) once the API has
  one, and answer `410` for notices that are gone for good.
- City × category landing pages (`/found/[city]/[category]`) with their own copy: that is
  where most organic traffic on a lost-and-found board lands, and filter URLs cannot rank for
  it because their canonical collapses onto the plain list.
