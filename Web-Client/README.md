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

The browser never calls the API. Server components and server actions call `GRAPHQL_URL`
from the server, and the only query surface exposed to the client is the `loadMoreItems`
server action behind the "show more" button. The one exception is images: they are served
straight from `NEXT_PUBLIC_FILES_URL` (the `/files/**` route of the API entry into the
bucket), which is why that origin is in `next.config.ts`'s `remotePatterns`. The API entry on
`:8080` is Caddy for now, not the `Getaway` module; see
[Caddy (API entry)](../__docs/deployment/infrastructure.md#caddy-api-entry).

## Types

`pnpm codegen` reads `../Client-API/src/main/resources/graphql/schema.graphqls` plus
`graphql/connections.graphqls`; the latter spells out the Relay connection types that
Spring for GraphQL registers at runtime and therefore keeps out of the schema file. No
running server is needed, so a schema change shows up as a diff here.

## Lost and found

What differs between the two kinds of notice is looked up, not branched on:

- `src/lib/kinds.ts`: `ITEM_KINDS`, the `ItemKind` type derived from it and the `isItemKind`
  guard for a kind that comes from a URL or a server action's arguments.
- `src/components/items/kind-meta.ts`: `KIND_META`, the tone, tint, icon, the other kind and the
  message key of every kind-specific text, grouped by the bundle namespace it lives in
  (`t(KIND_META[kind].list.title)` with the `list` translator). The keys are whole literals,
  never built from the kind, so searching for a key finds where it is used.
- `src/api/item-ops.ts`: `ITEM_OPS`, the GraphQL calls per kind (`list`, `one`, `create`,
  `claim`). The schema has a query and a mutation per kind; nothing outside this file needs to know.

Rules that differ by kind (a found notice needs a photo, a new find refreshes `stats`) stay in
the code that enforces them.

## Caching

Every GraphQL read in `src/api/` is a `fetch` cached by Next, and all the numbers live in
`src/lib/cache.ts`:

- `CACHE_TTL`: `minute` (lists, place suggestions), `hour` (home page `stats`, the sitemap),
  `sixHours` (one notice) and `day` (categories, countries, OG fonts).
  Pick the tier by how stale the data may get, not per call site.
- `CACHE_TAG` and `CACHE`: the tag and the policy (`{ revalidate, tags }`) of each read,
  passed straight to `graphqlRequest`.
- `invalidate(...tags)`: what server actions call after a write; it is `updateTag(tag)`, which
  expires the tag at once (read-your-own-writes): the author sees their new notice or claim on the
  next render instead of a stale page. It works only inside server actions.
- `NO_STORE`: mutations and per-visitor reads (claim status).

Two things Next does not make obvious. A fetch with a shorter `revalidate` shortens the whole
route, which is why the sitemap reads the lists through `CACHE.sitemapItems` (an hour) and not
`CACHE.items` (a minute). And route segment config such as `export const revalidate` has to be
a literal, so `sitemap.ts` repeats `3600` with a comment instead of importing `CACHE_TTL.hour`.
The hour bounds changes made outside the site (notices imported from Telegram, archiving in the
admin panel and by Worker); posts through the site drop the `items:<kind>` tag at once.

## Tests

Vitest, colocated as `<name>.test.ts`, no browser and no running backend:

- `i18n/messages.test.ts`: the five bundles have the same keys and arguments.
- `schema/report-schema.test.ts`, `schema/claim-schema.test.ts`: the client rules mirror
  Client-API's inputs (E.164 phone, email width, photo on a found notice, date window) and
  the mapping to `ItemInput` / `ContactInfoInput` sends empty optionals as `null`. The
  notice's email is optional (`ItemContactInfoInput`), the claimant's is not.
- `app/actions/report.test.ts`, `app/actions/claim.test.ts`: the server actions with
  `@/graphql/client`, `@/lib/bot-check`, `next/headers` and `next/cache` mocked: invalid
  input and bots never reach the API, the mutation variables, the 30-day
  `DERECHI_CLAIM_<kind>_<id>` cookie holding the claim id, `NOT_FOUND` versus other
  failures, which cache tags are revalidated, and `claimStatus` refusing a malformed id.
- `lib/claim-cookie.test.ts`, `lib/item-search.test.ts` (query string to filter and back),
  `lib/intl/country.test.ts`, `lib/intl/dates.test.ts`, `lib/seo.test.ts`.
- `app/sitemap.test.ts` with `@/api/items` mocked: a notice is listed once, by the URL in its own
  language (`/uk/` when there is none) and without `hreflang`, a month without notices lists the list page instead of an empty file, and a
  city page is among the pages only while the city has notices, a city's list only for the kind
  it has notices of.
- `lib/cities.test.ts`: slug lookup, a place on a city's edge falls into it, the city areas do not
  overlap, and every city in the registry has a name in the bundles.
- `lib/uploads/presign.test.ts` and `presign.integration.test.ts`, see "Uploads" below.

React components are not unit-tested; `async` server components are not supported by
Vitest, and the screens are checked by hand in `pnpm dev`.

## Errors

`graphqlRequest` logs nothing itself: the variables carry claimants' phones and emails. Its errors
name the operation (`ClaimLostItem: ...`), and `requestFailure` (`src/graphql/failure.ts`) turns one
into the reason a server action returns (`notFound`, `disposableEmail`, `failed`).

Server errors (server components, server actions, route handlers, the proxy) are logged by
`onRequestError` in `src/instrumentation.ts` as one JSON line each (`"event":"request_error"`, the
message, digest, stack, method, path without the query string, route), so the hosting's log view
can filter them. Request headers and the query string stay out: they carry cookies, claim ids and
search terms. Errors in the browser are not collected.

## Analytics

Vercel Web Analytics (`@vercel/analytics`) and Speed Insights (`@vercel/speed-insights`, Core Web
Vitals from real visits) both live in `SiteAnalytics` (`src/components/layout/site-analytics.tsx`),
loaded with `next/dynamic` and `ssr: false`. The locale layout renders it only when
`process.env.VERCEL` is set, so `next dev`, the e2e build and any other host load neither script.
Each one also has to be enabled in the Vercel project's dashboard; until then the component sends
nothing. Neither sets cookies.

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
`Client-API`'s `countries` query (`fetchCountries`, cached a day; during an outage it falls
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
form uses, where `emailOptional` marks the email as optional for the notice's author only). The detail page also renders inside the modal route's `RouteDialog`, which is why
the form is inline and not a nested dialog. Nothing the claimant types is shown anywhere;
`Client-API` stores it and Worker mails and texts it to the notice's author.

The card's question and button name the thing: `item.isItYours`, `item.didYouFind`,
`claim.sendMine` and `claim.sendFound` are ICU `select`s on the category key ("Це ваші ключі?",
"Улюбленець у мене, надіслати мої контакти"), with the generic "річ" as `other`, so a new
category reads generic until it gets its branches. `ClaimCard` passes `category` down through
`ClaimItemProvider`. Every other claim text avoids naming the thing ("тому, хто знайшов",
"власнику") and takes no `{category}`; how to word a new category is in
`__docs/extending/add-a-category.md`.

`claimNotice(kind, id, values)` in `src/app/actions/claim.ts` validates with `claimSchema`,
runs the same BotID check as `createNotice` (`src/lib/bot-check.ts`; the claim posts to the
notice page, so `/*/lost/*` and `/*/found/*` are in `instrumentation-client.ts`'s `protect`
list), calls `claimLostItem`/`claimFoundItem` and sets `DERECHI_CLAIM_<kind>_<id>` to the
claim's id for 30 days. The cookie is readable
by script on purpose: the page stays cacheable, the server render always shows the button, and
`ClaimCard` swaps in the "already sent" state on mount (`useClaim`, `hasClaimCookie`). Any value
counts, so old cookies holding `1`, a `DR-` payment code or a token still mean "already responded". A second claim with the same phone or
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
six hours run out, and a claim sent from it in the meantime answers `notFound`, which revalidates
that notice's tag.

### SMS outage notice

`NEXT_PUBLIC_SMS_OUTAGE=true` is a feature flag for when the SMS channel is down: opening the
claim form shows a warning toast once ("SMS is temporarily
unavailable, emails arrive as usual", `smsOutage.*` in the bundles, `useSmsOutageNotice`).
Nothing is blocked; the claim goes through as usual. Unset or `false` turns it
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

### The author's phone number (switched off)

The paid "Get the author's phone number" dialog (Fourthwall checkout) is removed from the web
client for now; Client-API and Worker still have the whole flow. What was removed and how to
bring it back: [`__docs/features/paid-author-number.md`](../__docs/features/paid-author-number.md).

## Legal texts per country

`src/content/legal/<locale>.json` holds the documents; the sentences that depend on the
country are slots: `{findersLaw}` (finder's duties and fee), `{governingLaw}`, `{dataLaw}`,
`{rightsBasis}` and `{complaintRight}`.
They are filled from `src/content/legal/jurisdictions/<CC>.json`, one file per supported country
with the five sentences in every locale and that country's `updated` date. Adding a country to
`derechi.countries.supported` therefore means adding one jurisdiction file; without it the
page falls back to the first jurisdiction and warns in the server log. A section may carry an
`id`, rendered as the `<section>`'s anchor.

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
`FieldError`). The texts are drafts for a lawyer; the operator and contact
email they name come from `LEGAL_CONTACT` in `src/content/legal/types.ts`. The privacy policy
still carries two placeholders, `[EMAIL AND SMS PROVIDER, HOSTING: TO BE ADDED]` and
`[SIMILAR FINDS NOTIFICATIONS: TO BE DESCRIBED]`, to be written once those are settled.

## Uploads

Photos go **straight from the browser to the bucket**: locally to MinIO through Caddy, in
production to Backblaze B2 directly. No image byte passes through Node, and `Client-API` needs
no upload endpoint.

The file is held in the browser while the form is filled in and travels **when the notice is
submitted**, not when it is chosen: an abandoned form then leaves nothing in the bucket, and
the photo is only stored once the rest of the notice has passed validation.

1. `createUploadTicket` (a server action) checks the type and size, generates the key
   (`items/yyyy/MM/<uuid>.<ext>`, the layout `Admin-API` already writes) and signs a PUT URL
   with SigV4, see `src/lib/uploads/presign.ts`. It signs with `S3_ACCESS_KEY`/`S3_SECRET_KEY`,
   MinIO's application account (`derechi-app` locally, created by `minio-init`), which may only
   read, write and delete objects in the bucket; never the root credentials.
2. The browser PUTs the file to `<S3_PUBLIC_ENDPOINT>/derechi-files/<key>?X-Amz-…`.
3. Locally Caddy (`docker/caddy/api.Caddyfile`) forwards `PUT` and the CORS preflight on
   `/derechi-files/**` to MinIO **with the `Host` header kept** and no path rewrite: the
   signature covers both, so changing either would break it. It also caps the body at 5 MiB
   (`413` above it), the same 5242880 bytes as `S3_MAX_UPLOAD_BYTES` in `.env.example`.
4. The notice stores the key; reads go through the `/files/**` route.

Because the key only exists after the upload, the browser validates the form with
`draftSchema` (the full schema minus `image`) and the "a found item needs a photo" rule is
checked against the picked file. The server action still runs the complete `reportSchema`, so
the contract with Client-API is unchanged.

`S3_PUBLIC_ENDPOINT` must therefore be the origin **the browser** uses (Caddy on `:8080`
locally, B2 in production), not MinIO's internal address: it is signed into the URL. Testing
from a phone on the same network means the LAN address (`http://192.168.x.y:8080`).

CORS is answered by the storage, not by Caddy: MinIO allows any origin by default, so a phone
on the LAN needs no extra setting, and in production the B2 bucket's own CORS rules have to
allow the site.

A `403` on the PUT comes from the storage, never from Caddy: MinIO answers with an XML body
naming the reason. `SignatureDoesNotMatch` usually means the browser reached a different
address than the one in `S3_PUBLIC_ENDPOINT`.

A `404` on the PUT comes from Caddy, not from MinIO: no route matched (MinIO would answer
`403` for a bad signature and `404` only for a missing bucket). `curl -i -X PUT
http://localhost:8080/derechi-files/probe` tells the two apart: `403` means the route is live
and MinIO rejected the unsigned request, `404` means the path or the method does not match,
or the running Caddy has an older config (`docker compose restart caddy`). A `502` means
Caddy cannot reach MinIO.

The signing is verified against a real MinIO rather than a golden string:

```bash
docker compose up -d minio minio-init caddy   # from the repo root

S3_TEST_ENDPOINT=http://localhost:9000 pnpm test                      # MinIO directly
# through Caddy (adds the 413 size-cap check)
S3_TEST_ENDPOINT=http://localhost:8080 S3_TEST_READ_URL=http://localhost:8080/files S3_TEST_MAX_BYTES=5242880 pnpm test
```

Without `S3_TEST_ENDPOINT` those tests skip, so `pnpm test` stays offline by default.

## Tour

driver.js, mounted per page with `<Tour id="home" />`. Steps point at `data-tour` attributes
rather than class names, and a step whose element is not on the current screen is dropped
instead of being shown against a page corner.

It runs itself once per visitor (`derechi.tour.<id>` in localStorage, through
`src/lib/browser-storage.ts` like everything else the site keeps in the browser, since private
windows throw) and afterwards only from the header button. That button comes
from `TourProvider`: a page with a tour registers its run function, and the button renders
only when one has, so it never offers a tour that is not there.

driver.js substitutes its own `{{current}}`/`{{total}}` in the progress text, which ICU would
refuse to parse, so the bundle keeps plain `{current}`/`{total}` arguments and the component
passes driver's tokens in as values.
## Search

Every page sets its own `canonical` and one `hreflang` per locale (plus `x-default`) through
`pageAlternates` in `src/lib/seo.ts`; the layout's alternates alone would point every page at
the locale's home. Notice pages get a title of the form `Keys – Lviv, 12 Sept 2026` (the notice's own title, no kind prefix),
breadcrumbs and an `ItemPage` in JSON-LD; the home page adds `WebSite` with a `SearchAction`.
JSON-LD goes into the page only through `jsonLd()` in `src/lib/seo.ts`: `JSON.stringify` leaves `<`
as is, so a notice titled `</script><script>…` would otherwise run as a script on its own page.

Notice pages are the exception to `hreflang`: the notice text is the same in every locale, so only
the copy in the notice's own language is indexed: `itemLocale(item.language)` in `src/lib/seo.ts`, where
`language` is what `Client-API` detected from the text when the notice was written, and `uk`
(`FALLBACK_ITEM_LOCALE`) when it was not detected or is not a site locale (`ru`, `es`, `it`). Each copy
keeps a self `canonical` and no `hreflang`, the other four answer `noindex, follow`, and the sitemap
lists every notice by that one URL.

A meta description is written for the results page, not reused from the visible text: the home page
has `app.homeDescription`, the lists `list.lostMetaDescription` and `list.foundMetaDescription`
(the header lines under their `<h1>` are separate keys), the report pages `form.metaDescriptionLost`
and `form.metaDescriptionFound` (`form.pageDescription` is the line under their `<h1>`), a city page
`cityPage.description`, the privacy page `privacy.metaDescription` in `src/content/legal/*.json` (one text for every country:
Google crawls without the cookie and from abroad, so it only ever sees the fallback jurisdiction). Keep
them under about 155 characters with the longest city in `{in}`, since Google cuts the rest. When
Google finds a description thin it builds a snippet from the page instead, so the list filters and
the counter row carry `data-nosnippet`: before that the found list came up as "… Пошук. Пошук.
Категорія Усі категорії.".

Each notice has its own `opengraph-image.tsx` (`src/screens/found_lost/item-og-image.tsx`): photo,
a lost/found plaque in the kind's colour (pink/mint, as on the list headers), the reward when there
is one, title, the short place with its city (`noticePlace`, the same as in the `<title>`) and date.
The plaque does repeat the verb most imported titles start with ("Загублено …"), but titles people
type themselves often do not, and in a feed the kind is the first thing a reader needs. Only JPEG
and PNG photos are drawn (`next/og` reads no WebP); the rest, and a notice without a photo, get the
category art in the photo's place, as on the site (see the share card below). Both cards load Nunito from
Google Fonts through `src/lib/og-font.ts`, subset to the card's text, so the cards use the site's
font rather than the `next/og` default (Geist). Other pages inherit the generic card from
`[locale]/opengraph-image.tsx`.

A notice page also offers two share buttons (`src/screens/found_lost/share-actions.tsx`). "Share"
opens the system share sheet with a short text and the link, or copies both where there is no sheet
(desktop). "Image with QR" fetches `/{locale}/{kind}/{id}/card.png`, a 1080×1080 square for feeds
and printing: plaque, reward, photo, title, place, date, the link and a QR code of it
(`src/lib/qr.ts`, `qrcode-generator`). A notice without a photo, or with one the card cannot draw,
shows the same category art as the site (`category-art.tsx`): the icon nodes come from the `lucide`
package, pinned to the `lucide-react` version, because `lucide-react` components read a React context
and do not render under `next/og`. The card carries no phone number on purpose: it spreads beyond our
control, and contact goes through the site. It answers `noindex` and is cached for an
hour. Where the sheet takes files it is shared as a file, otherwise downloaded. The file is fetched
ahead (on hover or focus, or at once after publishing), because the sheet only opens while the click
still counts as the person's own gesture. Right after a notice is published, the report form leaves
a mark in `sessionStorage` (`markJustPublished`), and the page shows the same buttons in a card
asking to share the notice in local groups, until it is closed.

### Guides

`/{locale}/guide` and `/{locale}/guide/{slug}` (`src/screens/guide/`) are long-form "what to do
if you lost X" pages, one per notice category, written for the person searching the web in the
first hour: a lead, sections with paragraphs, lists, callouts and numbered steps, a card that sends
them to the found notices of that category with a live rail of the latest ones, an FAQ, the home
page's city map with every pin landing on the "where to turn" section of the city page
(`#lost-property`, `CityMap`'s `hash` prop), and related guides. A sticky contents list sits
beside the article on wide screens and folds into a `<details>` above it on narrow ones. The
advice is general on purpose, the same in every country: which office, which phone number, which
transport operator belongs on the city pages, not here.

The texts live in `src/content/guides/` like the legal texts do: one JSON per locale with the
shared labels and a `guides` map, typed by `types.ts`, read through `guideTexts(locale)` in
`index.ts`, which also holds the slug list and the per-guide metadata (category, dates, related
guides). Icons are the only thing in `guide-icons.tsx`. `guides.test.ts` checks that every locale
carries every guide with the same sections, blocks and steps, so a translation cannot silently
drop a paragraph. A guide's `title` is both its `<h1>` and its `<title>`. The pages are in the
sitemap (`monthly`, 0.7), in the footer under "Guides", and linked from every notice of their
category; each carries `Article`, `FAQPage` and `BreadcrumbList` JSON-LD, the index a
`CollectionPage`. Adding a guide: a slug and metadata in `index.ts`, an icon, and the texts in all
five JSON files.

Nothing is indexable unless `NODE_ENV` is `production` **and** `NEXT_PUBLIC_SITE_URL` is a public
host (`INDEXABLE` in `src/lib/seo.ts`): `next dev`, a local `pnpm start` and a LAN address all
answer `noindex` on every page and `Disallow: /` in `robots.txt`, so a tunnel or a shared link
never puts `localhost:3000` into an index.

A notice older than `STALE_AFTER_DAYS` (30) answers `noindex, follow` and drops out of the sitemap.
It is the same age at which `Worker` archives the notice (`derechi.archive.max-age`), so the two
change together; the threshold only covers the gap until the next archiving run. The sitemap is split with
`generateSitemaps` into `pages` plus one chunk per kind and calendar month (`lost-0`,
`found-1`, …), each a date window walked with the cursor, and `robots.txt` lists all of them.
A month without notices lists its kind's list page instead: Search Console rejects a `<urlset>`
without a `<url>` ("Missing XML tag").

`/{locale}/{city}` (`src/app/[locale]/[city]`, `src/screens/city/city-page.tsx`) is a lost and found
page per city of `src/lib/cities.ts`: Kyiv and the regional centres not under occupation, each a
centre and a radius. It is the home page's sections without the map, put together in its own file
on purpose, so either page can diverge: the hero names the city ("Де речі у Львові?", the scene's
badge), and the search, the category chips and the rails' "see all" open the city's own lists,
`/{locale}/{city}/found` and `/{locale}/{city}/lost` (`src/app/[locale]/[city]/{found,lost}`). There
the city is the path, not coordinates in the query: `parseItemSearch(raw, city)` keeps the slug,
ignores `lat`/`lon`, and `toFilter` turns it into the city's centre and radius from
`src/lib/cities.ts` (`cityArea`), also for "show more", whose server action looks the slug up again.
The filters stay in the city (they keep the pathname), "Near me" is hidden, the title is
`list.*TitleIn` and the description `list.*MetaDescriptionIn`. A city's list without live notices
of its kind answers `noindex, follow` and stays out of `pages.xml`, and the city page itself is
listed while either kind has notices. On the city page and its lists the menu keeps to the city:
`navLinks` (`src/components/layout/nav-links.ts`) points the header's Lost and Found and the bottom
bar's tabs at `/{city}/lost` and `/{city}/found`, and adds "My city" (`nav.myCity`) at `/{city}`:
the header shows it before Lost, the bottom bar puts it in place of Home, with the `School` icon
instead of `House`. The city comes from the
address alone (`cityInPath`), so a notice page or the report form shows the general menu, and the
brand in the header always leads to `/`.

On the general `/lost` and `/found` (no city in the path, "Near me" off) Znaida offers a narrower
list (`src/screens/found_lost/city-hint.tsx`). She is the home page's roaming Znaida: the same
`useRoaming` from `mascot-roamer.tsx`, told to peek only over the top of a spot (`topOnly`), to
leave room for the bubble above her head (`headroom`) and to stay until she is closed (`stay`).
The spots are the list's cards (`data-mascot-spot` in `ItemList`, which only the list pages use).
`LazyCityHint` waits for a 240 px scroll and only then loads her with `next/dynamic`
(`ssr: false`), so a paused hint or an unscrolled list costs no code. The city is
Vercel's guess from the IP address: `requestCity()` (`src/lib/geo.server.ts`) reads
`x-vercel-ip-latitude`/`-longitude` and keeps it only if `nearestCity` puts it inside a city's
radius. With a city she offers its list and "Near me", without one only "Near me", which runs the
same `useNearMe` as the switch. An IP address is often wrong on mobile networks (the operator's
gateway) and behind a VPN, so the city is only a suggestion and is never stored. Closing her, or
taking either offer, rests her for an hour (`pauseCityHint`, `derechi.city-hint` in `localStorage`);
without storage she stays away. She is `z-40`, above the sticky filters (`z-30`): she keeps the place
where she appeared while the list scrolls, so her bubble slides up across the filters, and under
them its buttons could not be pressed. Locally and in e2e there are no such headers, so there is no city:
the e2e spec sets them with `setExtraHTTPHeaders`, as the country test does.

Its JSON-LD is the home page's `WebSite` plus a `CollectionPage`
about the `City`, with a search around it and the breadcrumbs (`cityPageJsonLd` in `src/lib/seo.ts`).
Only the registry's slugs exist (`dynamicParams = false`), any other single segment is still a 404.
A city without live notices answers `noindex, follow` and stays out of `pages.xml` (`cityHasNotices`
in `src/api/cities.ts`, which counts a failed query as not empty).

The home page links the cities from a map (`src/screens/home/city-map`): a static outline of Ukraine
styled as a fridge magnet, with a purple `MapPin` per city. With a mouse (`pointer-fine`) a city's
name shows on hover or focus; on touch screens every name is always shown, at 9px on a phone, where
a hyphenated name wraps (`w-min`) and Ivano-Frankivsk, Lutsk and Khmelnytskyi keep their pin but not
the name (`UNLABELLED_ON_PHONE`), as there is no room in the west. `LABEL_SIDE` puts the other names
where they do not cover a neighbour on a phone. Below `lg` a "choose a city" select under the map
opens the page too.
`outline.ts` is generated: Natural Earth 1:10m countries in Ukraine's point of view (Crimea and the
whole east within Ukraine, public domain), the main polygon simplified (Douglas-Peucker, 5 units of
1000) and smoothed with quadratic curves, projected equirectangularly at 48.4°N; `MAP` keeps the
projection, so a pin is placed from a city's `lat`/`lon` alone. A notice page links the city its
coordinates fall into (`cityOf`); that city's name is also added
after the place in the notice's `<title>` (`withCity`: "Привокзальний ринок, Львів"), since people
search by city, not by street, unless the place already names it as a whole word ("Тернопільська
школа" does not). The title keeps only the first part of the address (`shortPlace`, cut to 40
characters) and no date, so it fits the ~60 characters a search result shows; the description
carries the whole address without the postcode (`tidyPlace`) and the date. Names and the
"in <city>" forms are in the bundles' `city` namespace, so a new city is a registry entry plus
`city.<slug>` in all five bundles (`cities.test.ts` checks that they match).

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
