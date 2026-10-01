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
   lost one. It expands an inline form: phone, email, messengers, the consent line linking to the
   terms and the privacy policy for the country of the notice's place.
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
viewer ──claimLostItem/claimFoundItem──▶ Client-API ──item.<kind>.claimed──▶ Automatic-Search
                                            │                                     │
                                            │ lost_item_claim / found_item_claim  │ notification.claim.created
                                            ▼                                     ▼
                                        PostgreSQL                           Notification ──▶ author (email + SMS)

day +1   Automatic-Search job ──notification.claim.reminder──▶ author:   "is it back? close the notice: <link>"
day +2   Automatic-Search job ──notification.claim.reminder──▶ claimant: "did you get it back? <link>"
link     Web-Client ──confirmReturn(token)──▶ Client-API ──item.<kind>.returned──▶ Automatic-Search archives the notice
day +7   Automatic-Search job archives the notice if nobody confirmed (counted from the newest claim)
```

### Client-API

Three mutations in `schema.graphqls`, handled by `ClaimController`:

| Mutation | Behaviour |
|---|---|
| `claimLostItem(id, contact)` | the viewer found the lost item; the owner is notified |
| `claimFoundItem(id, contact)` | the viewer owns the found item; the finder is notified |
| `confirmReturn(token)` | from a reminder link; stamps `confirmed_at` and asks for the archive |

`contact` is the same `ContactInfoInput` as in `ItemInput` (E.164 phone, email, optional
messengers) with the same validation. Unknown item or token → `NOT_FOUND`.

`ClaimService` (one subclass per kind) looks for an existing claim on the item with the same phone
**or** email; if there is one it returns its id with `repeated: true` and publishes nothing.
Otherwise it saves a new `contact_info` row, the claim (`token` = random UUID, `created_at`), and
publishes a Spring application event that `ClaimEventPublisher` turns into `ClaimEvent { id }`
on `derechi.items` **after commit** (`@TransactionalEventListener(AFTER_COMMIT)`), routing key
`item.lost.claimed` or `item.found.claimed`. The consumer reads the row, so publishing before the
commit would race it. `confirmReturn` does the same with `item.<kind>.returned`; a token that is
already confirmed returns `true` and publishes nothing.

### Automatic-Search

The worker gained a second queue, `automatic-search.claims`, bound with `item.*.claimed` and
`item.*.returned`, and a scheduled job.

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
  5. deletes claims older than `retention` (1 year) with their contact infos, a safety net in case
     step 4 is disabled.
  Each claim is handled in its own transaction, publish first and stamp second: a RabbitMQ outage
  is retried on the next run, and a stamp failure at worst repeats a message inside NotifyHub's
  one-hour dedup window.
- `ItemArchiver` does what the admin "Archive" does: copies the item into `*_item_history` with
  `archived_at`, deletes its `similar_item` rows, deletes its claims **and their contact infos**,
  deletes the item. To do this the module now carries `LostItemHistory`/`FoundItemHistory` and
  `Thing` has `compensation` and `currency` like the other copies.

Config, `Automatic-Search/src/main/resources/application.yaml`:

```yaml
derechi:
  claims:
    queue: automatic-search.claims
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

Read-only. The lost and found lists show a tag with the number of responses next to the title,
and the item dialog lists them: phone with messenger icons, email, when, and a status tag
(new / reminded / confirmed). Archiving or deleting a notice deletes its claims and their contact
infos first (`AdminItemService.deleteClaims`, next to `deleteMatches`); the archives have none.

## Storage

`lost_item_claim` and `found_item_claim` (migration `006-item-claims`), one row per response:
the item, the claimant's `contact_info_id`, `token`, `created_at`, `author_reminded_at`,
`claimant_reminded_at`, `confirmed_at`. Plain foreign keys without cascade: whoever deletes an
item must delete its claims first, and a forgotten clean-up fails loudly instead of leaving
orphaned contacts.

The claimant's contacts therefore live exactly as long as the notice, which with claims is at
most about a week after the last response, and never more than a year. The privacy policy on the
web client says so.

## Abuse

- Vercel BotID on the server action, same as for posting a notice.
- Same phone or email on the same notice: no second message, `repeated: true`.
- The 1-hour cookie hides the button after a response.
- NotifyHub dedup drops an exact replay of the same event within an hour.

There is no rate limit per IP yet, and nothing stops a person from sending someone else's phone
number; the terms say the contacts must be your own, and the message to the author says to
check details before meeting.

## Known gaps

- SMS is still not wired in `Notification` (no provider); authors get email only until it is.
- Messengers are only listed as text in the message; nothing is sent through them.
- The author cannot see the responses anywhere except the messages; there is no "my notice" page.
- The web client's detail page is cached for an hour, so an archived notice can stay visible
  for up to that long after a confirmation (`confirmReturn` revalidates the list tags).

## Where to look

- `Client-API/src/main/java/org/shpytchuk/clientapi/service/ClaimService.java`, `ReturnService.java`, `controller/ClaimController.java`, `event/ClaimEventPublisher.java`
- `Automatic-Search/src/main/java/org/shpytchuk/automaticsearch/listener/ClaimListener.java`, `service/ClaimNotifier.java`, `service/ClaimFollowUpJob.java` (+ `ClaimFollowUps`, one transaction per claim), `service/ItemArchiver.java`, `language/PhoneLocales.java`, `src/main/resources/messages*.properties`
- `Notification/src/main/java/org/shpytchuk/notification/service/NotificationSender.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/service/AdminItemService.java`, `templates/fragments/dialogs.html`
- `Web-Client/src/screens/found_lost/claim-card.tsx`, `src/screens/claims/confirm-return.tsx`, `src/app/actions/claim.ts`
- `DB-Postgres/changelog/changes/006-item-claims.postgresql.sql`
- `docker/rabbitmq/definitions.json`
- Tests: `ClaimControllerTests`, `ClaimNotifierTest`, `ClaimFollowUpJobTest`, `PhoneLocalesTest`, `NotificationSenderTest`, `LostItemAdminServiceTest`
