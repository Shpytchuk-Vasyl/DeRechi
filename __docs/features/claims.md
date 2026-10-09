# Claims

A **claim** is a person responding to a notice: "it's mine" on a found item, "I found it" on a
lost item. They leave their own phone, email and messengers, and the system passes those to the
notice author by email and SMS. Nothing of the claimant is ever shown publicly, which is why the
public site no longer promises to "unlock" an author's full contacts: the author decides whom to
call back.

The claim also tells us the notice is probably done. Both sides are asked to confirm the return,
and a notice with claims that nobody confirms is archived a week after the last one.

## What a user sees

On a notice page (`Web-Client`, `ClaimCard` in `src/screens/found_lost/claim-card.tsx`):

1. The author's masked phone and email, as before.
2. A button, "It's mine, send my contacts" on a found notice, "I found it, send my contacts" on a
   lost one. It expands an inline form: phone, email, messengers, and a required consent checkbox
   linking to the terms and the privacy policy for the country of the notice's place. The form
   does not submit until the box is ticked; the checkbox is front-end only (see
   `Web-Client/README.md`), so Client-API gets no consent field.
3. After submitting: "We passed your contacts to the finder/owner. They'll get in touch." The
   server action also sets a cookie `DERECHI_CLAIM_<kind>_<id>` for an hour, so reloading the
   page shows the same state instead of the button. A repeat with the same phone or email is
   answered with `repeated: true` and the "you already responded" text; nothing is sent twice.
4. A small link stays under the block for people who want to post their own notice instead.

The reminders carry a link to `/{lang}/claims/{token}`. That page does **not** act on load, because
mail scanners follow links: it shows a button, "Yes, close the notice", and only the click calls
`confirmReturn`. Unknown or spent token: "This link is no longer valid".

## The flow

```
viewer ──claimLostItem/claimFoundItem──▶ Client-API ──item.<kind>.claimed──▶ Worker
                                            │                                     │
                                            │ lost_item_claim / found_item_claim  │ notification.claim.created
                                            ▼                                     ▼
                                        PostgreSQL                           Notification ──▶ author (email + SMS)

day +1   Worker job ──notification.claim.reminder──▶ author:   "is it back? close the notice: <link>"
day +2   Worker job ──notification.claim.reminder──▶ claimant: "did you get it back? <link>"
link     Web-Client ──confirmReturn(token)──▶ Client-API ──item.<kind>.returned──▶ Worker archives the notice
day +7   Worker job archives the notice if nobody confirmed (counted from the newest claim)
admin    Admin-API ──item.<kind>.archive──▶ Worker archives the notice (same code path)
```

### Client-API

Three mutations in `schema.graphqls`, handled by `ClaimController`:

| Mutation | Behaviour |
|---|---|
| `claimLostItem(id, contact)` | the viewer found the lost item; the owner is notified |
| `claimFoundItem(id, contact)` | the viewer owns the found item; the finder is notified |
| `confirmReturn(token)` | from a reminder link; stamps `confirmed_at` and asks for the archive |
| `unlockLostItemClaim(itemId, id)`, `unlockFoundItemClaim(itemId, id)` | prepares the payment for the author's phone number (see below) |

| Query | Behaviour |
|---|---|
| `lostItemClaim(itemId, id)`, `foundItemClaim(itemId, id)` | the unlock state of a claim; `null` when the notice has no such claim |

A claim is addressed by its notice and its own id. The `token` is internal: it is not part of the
`Claim` type and only travels in the reminder links, where it closes the notice. Guessing ids only
shows someone else's unlock state; the author's number itself only ever goes by SMS and email to
the contacts the claimant left.

Everything that archives goes through Worker, which is why the admin panel also only
*asks* (see below): one archiver, one place that knows how claims follow a notice into history.

`contact` is `ContactInfoInput` (E.164 phone, email, optional messengers). Unlike
`ItemContactInfoInput` of a notice, the email here is required: the author's phone number is
sent to it after payment. The record normalises the email itself (`EmailNormalizer`: lower case,
no `+tag` on any domain, and on Gmail no dots and `googlemail.com` as `gmail.com`), and it is stored
that way, so `vasyl+12@gmail.com` is the same claimant as `vasyl@gmail.com` for repeats and the
unlock limit. Mail goes to the normalised address, which is the same mailbox. Unknown item or token → `NOT_FOUND`.

`ClaimService` (one subclass per kind) loads the item with a plain `findById` and looks for an
existing claim on the item with the same phone **or** email. There is no lock today, so two
concurrent identical claims can both miss each other and both be created. Planned: a
`PESSIMISTIC_WRITE` lock on the item row (a unique index cannot do it, the phone and email live in
`contact_info`). If a matching claim exists it returns its id with `repeated: true` and publishes nothing.
Otherwise it saves a new `contact_info` row, the claim (`token` = random UUID, `created_at`), and
returns `ClaimDto(id, repeated = false)`. `ClaimEventAspect`, an `@AfterReturning` advice on
`ClaimService+.claim(..)` and `confirm(..)` with `@Order(0)` (outside the transaction proxy, like
`ItemEventAspect`), publishes `ClaimEvent { id }` on `derechi.items` with `item.lost.claimed` or
`item.found.claimed` once the transaction has committed; a `repeated` result publishes nothing.
The consumer reads the row, so publishing before the commit would race it. `confirm(token)`
returns the claim the same way, with `repeated = true` for a token that was already confirmed,
and the aspect turns a fresh confirmation into `item.<kind>.returned`. `ReturnService`, which
tries both kinds, deliberately has no transaction of its own: the per-kind transaction must be
the outermost one, or the advice would fire before the commit.

### Worker

The worker gained a second queue, `worker.claims`, bound with `item.*.claimed`,
`item.*.returned` and `item.*.paid` (dead letters to `worker.claims.dlq`), and a scheduled job.

- `ClaimListener` dispatches by routing key like `ItemCreatedListener`. `claimed` loads the claim
  with both contact infos and calls `ClaimNotifier.notifyAuthor`; `returned` calls
  `ItemArchiver.archive`. Both are harmless on replay: a missing claim or item is logged and
  skipped.
- `ClaimNotifier` builds `NotificationRequestedEvent`s from this module's own message bundles
  (`messages*.properties`, five languages) and publishes them to `derechi.notifications`:
  `notification.claim.created` with the claimant's contacts, `notification.claim.reminder` for
  both reminders. Dedup keys: `claim:<kind>:<id>`, `...:author-reminder`, `...:claimant-reminder`.
- **Language comes from the recipient's phone number**, not from any UI locale: `PhoneLocales`
  maps the libphonenumber region to a language (UA → uk, PL → pl, DE/AT/CH → de, FR/BE/LU/MC → fr,
  anything else → en). A Ukrainian with a German number gets German, which is the decision: the
  number says where the person lives.
- `ClaimFollowUpJob` runs every `derechi.claims.check-every` (10 minutes) and, per kind:
  1. reminds authors of claims older than `author-reminder-after` (1 day) and stamps `author_reminded_at`;
  2. reminds claimants `claimant-reminder-after` (1 day) after the author reminder, stamps `claimant_reminded_at`;
  3. archives items that have a confirmed claim (fallback for a lost `returned` event);
  4. archives items whose **newest** claim is older than `archive-after` (7 days);
  5. deletes claims older than `retention` (1 year), archived or not, with their contact infos:
     this is what keeps the privacy policy's "one year at most" true.
  Each claim is handled in its own transaction, publish first and stamp second: a RabbitMQ outage
  is retried on the next run, and a stamp failure at worst repeats a message inside NotifyHub's
  one-hour dedup window (in memory, per `Notification` instance, lost on restart).
  `run()` carries `@SchedulerLock(name = "claim-follow-up")` (ShedLock, JDBC provider, table
  `shedlock` from migration `009-shedlock`), so with several Worker instances only one runs the job
  at a time.
- `ItemArchiver` is the **only** code that archives a notice; the admin "Archive" button sends an
  `ARCHIVE_REQUESTED` event (`item.<kind>.archive`, queue `worker.archive`,
  `ArchiveListener`) and this class runs it, as it does for a confirmed return and for the job.
  It copies the item into `*_item_history` with `archived_at`, deletes its `similar_item` rows,
  re-points its claims at the history copy (`item_id` → `null`, `archived_item_id` →
  the new id) and deletes the item. The claimants' contacts stay for the retention period. To do
  this the module carries `LostItemHistory`/`FoundItemHistory` and `Thing` has `compensation`
  and `currency` like the other copies.

Config, `Worker/src/main/resources/application.yaml`:

```yaml
derechi:
  claims:
    queue: worker.claims
    exchange: derechi.notifications
    site-url: ${DERECHI_SITE_URL:http://localhost:3000}   # links in messages
    check-every: PT10M
    author-reminder-after: P1D
    claimant-reminder-after: P1D
    archive-after: P7D
    retention: P365D
```

`DERECHI_SITE_URL` is the public address of the web client; Compose sets it to
`http://localhost:3000`.

### Notification

Unchanged contract, two fixes that this feature needed:

- the NotifyHub dedup key is now `<event.deduplicationKey>:<CHANNEL>` instead of
  `<recipient>:<CHANNEL>`, so two different claims reaching the same author within an hour both go
  out, while a replay of the same event is still dropped;
- a channel that is not configured under `notify.channels.*` is skipped with a warning instead of
  failing the message. Every claim notification carries a phone, and SMS has no provider yet;
  before the fix such a message would have gone to the DLQ after the email was already sent.

### Admin-API

Read-only. The lost and found lists, and the two archives, show a tag with the number of
responses next to the title, and the item dialog lists them: phone with messenger icons, email,
when, and a status tag (new / reminded / confirmed). The archive pages look claims up by the
history copy.

"Archive" no longer copies anything itself: `AdminItemService.archive(id, actor)` checks the
notice exists and publishes `ArchiveRequestedEvent` to `derechi.items` with
`item.<kind>.archive`; Worker does the work a moment later, so the flash message says
the notice is *being* archived and the row leaves the list on the next load. If RabbitMQ is down
the button fails like "Notify" does. "Delete" stays synchronous and final: it deletes the matches,
the claims **and their contact infos** (`deleteClaims`, next to `deleteMatches`), then the row.

## Getting the author's phone number (paid, through Fourthwall)

A responder never sees the author's contacts for free; the author decides whom to call back.
After responding, the notice page offers "Get the author's phone number": a small dialog explains
that the number will be sent by SMS and email to the contacts the responder gave, that this is how
the $1 payment keeps the service free, and shows a "Continue to payment" button that opens the
Fourthwall checkout in a new tab. That button only works once the responder ticks a box agreeing
that the number is sent right after payment and acknowledging that this ends the 14-day right of
withdrawal: for digital content EU consumer law (PL, DE, FR) waives that right only with such
consent given before payment, so the consent and the acknowledgement are in the label itself and
not only in the terms; the label links to the terms' section 5 (`#author-number`), which spells out
the 14 days and the statute per country (`withdrawalLaw` in the jurisdiction files). Unticked, the button shows a field error under the box instead of
the checkout; the tick is not stored and is cleared whenever the dialog opens. A claimant can open one checkout a week (`UNLOCK_LIMIT`); the
dialog then says when the next one is possible instead of offering a retry. Only the phone number is sent; the author's email and messengers stay private. The
dialog polls the claim status every 20 seconds and switches to "payment received" and then "sent to
your email and phone".

Why a product per response: Fourthwall has no custom checkout fields and no signed redirect, but
it has a Platform API that creates products and a signed `ORDER_PLACED` webhook that names the
product and variant bought. So each response that asks for the number gets its own hidden digital
product, and the variant id in the order is what ties the payment to the response. Nobody types a
code, and the payer's email does not matter.

The flow:

1. Opening the dialog calls `unlockLostItemClaim` or `unlockFoundItemClaim` with the notice id and
   the claim id. `ClaimUnlockService` finds the claim on that notice, and if it is unpaid and has no product yet, asks
   `FourthwallClient` for one: `POST /open-api/v1.0/products` with `type: digital`,
   `publishOnCreate: false`, the name `derechi.fourthwall.product-name` with the claim reference
   (`lost-42`), then, if `derechi.fourthwall.product-image.url` (`FOURTHWALL_PRODUCT_IMAGE_URL`) is
   set, `POST /products/{id}/images` with that media-library file as the checkout thumbnail. Create
   takes no images, but this call answers with the full product, so the variant id comes from it;
   only without an image (or when attaching failed) does `GET /products/{id}` read it. Two calls
   either way, plus three for the product file below. The image (600x800, the shop's 3:4 product photos) is uploaded once per shop through
   the media library API (`POST /media/upload-url`, `PUT` the bytes, `POST /media/images`). The URL
   to configure is the `uri` that `GET /media/images` lists on `cdn.fourthwall.com`, not the upload's
   `fileUrl`, which points to a temporary bucket. The image is decoration: if attaching it fails, the product is sold without it and
   the log warns. Then, if `derechi.fourthwall.product-file.text` is set (YAML only), the product gets
   that text as its downloadable file (`product-file.name`, `text/plain`): `POST
   /products/{id}/digital-files/upload-url`, `PUT` the bytes to the signed Google Cloud Storage URL,
   `POST /products/{id}/digital-files`. The `PUT` goes through a second `RestClient` without the
   Basic auth, so the API credentials never reach Google, and takes the URL as a `URI`, since a
   string would be encoded again and break the signature. Fourthwall lets the buyer cancel a digital
   order for 30 days until its file is downloaded; the file gives the buyer something to download.
   It only closes the cancel once the buyer actually downloads it. A failure warns and the product is
   sold without a file, like the image. Both ids are stored on the claim
   (`payment_product_id`, `payment_variant_id`) and the checkout comes back as
   `Claim.checkoutUrl`: `<shop>/cart/checkout?products=<variantId>:1`. The product is created
   outside any transaction; a second click reuses it. Fourthwall allows 5 product creations a
   minute per shop, so a 429, a 5xx or a timeout (`derechi.fourthwall.connect-timeout` 3s,
   `read-timeout` 10s) surfaces as the GraphQL error
   `PAYMENT_UNAVAILABLE` and the dialog offers to try again. The product is kept after the payment
   on purpose: it is the shop's record of the sale.

   One claimant opens at most `derechi.claims.unlock-limit` checkouts (1) per
   `derechi.claims.unlock-window` (P7D), so buying authors' numbers in bulk does not pay. The
   claimant is the phone **or** the normalised email of the claim, over both claim tables
   and archived claims too. Every checkout counts, paid or not, which also keeps one claimant from
   using up the Fourthwall product limit. Before the product is created, `ClaimService.reserveCheckout` stamps
   the claim's `payment_requested_at`, after `ClaimUnlockLimiter` has counted the claimant's stamps
   inside the window (`ClaimRepository.findPaymentRequestedSince`, once per claim table); a claim
   that is stamped already is not counted again, and a failed Fourthwall call clears the stamp.
   There is no lock: two unlocks by one claimant on different claims at the same moment can both
   pass the count. Over the limit the mutation fails with the GraphQL error
   `UNLOCK_LIMIT`, `extensions.retryAfter` holding the ISO-8601 instant when the next checkout is
   allowed. A claim that already has its checkout keeps getting it back, limit or not.
2. Fourthwall posts `ORDER_PLACED` to `POST /api/client/webhooks/fourthwall` (through the gateway's
   `/api/client/**` route). `FourthwallWebhookController` checks `X-Fourthwall-Hmac-SHA256`
   (HMAC-SHA256 of the raw body, base64, key `derechi.fourthwall.webhook-secret`), answers 401
   otherwise, ignores other event types with 200 and stores every accepted order in
   `fourthwall_order` keyed by the order id, so a retried webhook is a no-op.
3. `FourthwallOrderService` matches the order to the claim whose product or variant id is among
   the items bought (`offers[].id`, `offers[].variant.id`, `variants[].id`), skips `CANCELLED`
   orders, and leaves anything else unmatched with a warning in the log. Orders sent from the
   dashboard's "Send test notification" carry `testMode: true` and are stored with that flag.
4. A match stamps `paid_at` through `ClaimService.markPaid`, and `ClaimEventAspect` publishes
   `item.<kind>.paid` after the commit, exactly like `claimed` and `returned`. A second order for
   an already paid claim is stored but publishes nothing.
   Right after, `FourthwallClient.markDownloaded` calls `PUT /order/{id}/downloaded` with
   `defaultFileUrl` = the notice page (`derechi.site.url` + `/<kind>/<itemId>`, the site root for an
   archived notice). Fourthwall lets the buyer cancel a digital order, and get the money back, for
   30 days unless its file was downloaded, so without this the number could be bought and refunded.
   With a product file the call marks that file downloaded (`defaultFileUrl` is used only when the
   order has no download), which closes the self-service cancel; the shop can still cancel from the
   dashboard (the terms promise a refund when the number has not arrived within 24 hours). A failure
   only logs a warning, the payment is recorded either way. The API user needs `order_write`, and the
   Basic auth API user does NOT have it: Fourthwall answers `403` with
   `WWW-Authenticate: Bearer error="insufficient_scope"`, for any order id, although the docs say the
   API user has unrestricted access. Until Fourthwall grants it, the product file is what closes the
   cancel.
5. `Worker` (`PaidHandler` on `worker.claims`) sends the author's phone number to the responder,
   `notification.claim.unlocked`, in the responder's language, dedup `claim:<kind>:<id>:unlocked`,
   and stamps `contacts_sent_at`. If the notice was archived in the meantime the number comes from
   the history copy. The author is not notified.

The admin item dialog shows the response as "Paid" once the payment arrived.

Settings on the Fourthwall side: an API user (Settings, For developers, Open API) gives the basic
auth pair `FOURTHWALL_API_USERNAME` / `FOURTHWALL_API_PASSWORD`; a webhook on the same page with
the URL above and the `ORDER_PLACED` event gives `FOURTHWALL_WEBHOOK_SECRET`; the shop domain is
`FOURTHWALL_SHOP_URL`. Prices are in USD only; Fourthwall shows the local currency itself.

## Storage

`lost_item_claim` and `found_item_claim` (migrations `006-item-claims`, `007-claim-payment`,
`008-claim-unlock-limit`), one row per response:
`item_id` **or** `archived_item_id` (a `CHECK` makes it exactly one), the claimant's
`contact_info_id`, `token`, `created_at`, `author_reminded_at`, `claimant_reminded_at`,
`confirmed_at`, plus `payment_product_id`, `payment_variant_id`, `payment_requested_at`, `paid_at`
and `contacts_sent_at` for the paid phone number. `fourthwall_order` keeps every accepted `ORDER_PLACED` webhook (`order_id`
unique, friendly id, status, payer email and name, amount, currency, test flag, and the claim it was
matched to, if any; the link is dropped when the claim is deleted). While the notice is published
the claim points at it; archiving re-points it at
the history copy, so the claim and the claimant's contacts survive and the archive pages can show
who responded and whether the return was confirmed. Plain foreign keys without cascade: deleting a
notice outright must delete its claims first, and a forgotten clean-up fails loudly instead of
leaving orphaned contacts.

The claimant's contacts live for `derechi.claims.retention` (one year) from the response, live or
archived; the follow-up job deletes the claim and its `contact_info` row after that. Deleting a
notice from the admin panel deletes them at once. The privacy policy on the web client says so.

## Abuse

- Vercel BotID on the server action, same as for posting a notice.
- Same phone or email on the same notice: no second message, `repeated: true`.
- The 1-hour cookie hides the button after a response.
- NotifyHub dedup drops an exact replay of the same event within an hour (in memory, per
  `Notification` instance, so a restart or a second instance lets a replay through).
- Two identical claims sent at the same moment can both be created: there is no lock on the item
  row yet (planned, see above).

There is no rate limit per IP yet, and nothing stops a person from sending someone else's phone
number; the terms say the contacts must be your own, and the message to the author says to
check details before meeting.

## Known gaps

- An order that names no known product (a test notification, a product created by hand in the
  dashboard) stays unmatched; there is no admin page for unmatched `fourthwall_order` rows yet, only
  the log.
- Product creation is capped at 5 a minute per shop; the dialog asks to try again, there is no
  queue or pool of ready products yet.
- SMS has no provider, so until it has one the author's phone number reaches the responder by email only.

- SMS is still not wired in `Notification` (no provider); authors get email only until it is.
- Messengers are only listed as text in the message; nothing is sent through them.
- The author cannot see the responses anywhere except the messages; there is no "my notice" page.
- The web client's detail page is cached for an hour, so an archived notice can stay visible
  for up to that long after a confirmation (`confirmReturn` revalidates the list tags).

## Where to look

- `Client-API/src/main/java/org/shpytchuk/clientapi/service/ClaimService.java`, `ReturnService.java`, `controller/ClaimController.java`, `aspect/ClaimEventAspect.java`, `service/payment/ClaimUnlockService.java`, `service/payment/FourthwallOrderService.java`, `controller/payment/FourthwallWebhookController.java`, `client/FourthwallClient.java`, `config/FourthwallProperties.java`
- `Worker/src/main/java/org/shpytchuk/worker/listener/ClaimListener.java`, `listener/ArchiveListener.java`, `handler/ClaimedHandler.java`, `handler/ReturnedHandler.java`, `service/ClaimNotifier.java`, `cron/ClaimFollowUpJob.java` (+ `ClaimFollowUps`, one transaction per claim), `service/ItemArchiver.java`, `language/PhoneLocales.java`, `src/main/resources/messages*.properties`
- `Notification/src/main/java/org/shpytchuk/notification/service/NotificationSender.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/service/AdminItemService.java`, `templates/fragments/dialogs.html`
- `Web-Client/src/screens/found_lost/claim-card.tsx`, `src/screens/claims/confirm-return.tsx`, `src/app/actions/claim.ts`
- `DB-Postgres/changelog/changes/006-item-claims.postgresql.sql`, `007-claim-payment.postgresql.sql`, `008-claim-unlock-limit.postgresql.sql`, `009-shedlock.postgresql.sql`
- `docker/rabbitmq/definitions.json`
- Tests: `ClaimControllerTests`, `FourthwallWebhookControllerTests`, `FourthwallClientTest`, `FourthwallOrderPlacedTest`, `FourthwallPropertiesTest`, `ClaimEventAspectTest`, `ClaimNotifierTest`, `ClaimFollowUpJobTest`, `ClaimFollowUpsTests`, `ClaimRepositoryTests` (Worker), `ClaimHandlersTest`, `ItemArchiverTest`, `ItemArchiverTests`, `ArchiveListenerTest`, `PhoneLocalesTest`, `NotificationSenderTest`, `LostItemAdminServiceTest`, `AdminItemServiceTests`, `ItemClaimsTests`, and in `Web-Client` `claim.test.ts`, `claim-schema.test.ts`, `claim-cookie.test.ts`
