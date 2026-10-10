# Paid author's number (switched off in the web client)

Until 2026-10-10 a responder could pay $1 on Fourthwall to get a notice author's phone number
by SMS and email. The web client is hosted on Vercel's Hobby plan, which allows
non-commercial use only, so the paid part is **removed from the web client** until the site
moves to a plan that allows payments. Responding to a notice works as before: the claimant's
contacts go to the author, and the author decides whom to call back.

How the whole flow works (Client-API, Worker, Fourthwall settings, storage) is still described
in [Claims](claims.md#getting-the-authors-phone-number-paid-through-fourthwall).

## What is still there

Nothing on the back end changed, so the feature comes back by restoring the web client only.

| Module | State |
|---|---|
| Client-API | unchanged: `unlockLostItemClaim` / `unlockFoundItemClaim`, `lostItemClaim` / `foundItemClaim`, the `checkoutUrl` / `paid` / `contactsSent` fields on `Claim`, `PAYMENT_UNAVAILABLE` and `UNLOCK_LIMIT`, the Fourthwall client and the `POST /api/client/webhooks/fourthwall` webhook |
| Worker | unchanged: `PaidHandler` sends the number on `item.<kind>.paid` |
| DB-Postgres | unchanged: `007-claim-payment`, `008-claim-unlock-limit`, `fourthwall_order` |
| Web-Client | the dialog, its server actions, the GraphQL documents, the translations, the playground bench and the paid parts of the legal texts are removed (list below) |

Nobody can reach the checkout from the site any more. The GraphQL mutations stay callable by
anyone who sends them to the API directly; Fourthwall products are only created that way.

## What was removed from Web-Client

Everything below is one commit, `26f3f08` ("disable Claim feature"), on top of `cc4f0fc`, the
last commit with the feature.

| File | Change |
|---|---|
| `src/screens/found_lost/claim/claim-unlock.tsx` | deleted: the "Get the author's phone number" button and dialog (`ClaimUnlock`, `PhoneUnlock`), the waiver checkbox, polling |
| `src/screens/found_lost/claim/claim-done.tsx`, `claim-card.tsx` | no `unlock` / `onUnlockChange` props |
| `src/screens/found_lost/claim/use-claim.ts` | only `sent` / `markSent` left; reads the cookie with `hasClaimCookie` and no longer calls `claimStatus` |
| `src/app/actions/claim.ts` (+ `claim.test.ts`) | `claimStatus`, `unlockClaim`, `ClaimStatus`, `UnlockResult`, `isClaimId` removed; `ClaimResult` lost `checkoutUrl` / `paid` / `contactsSent` |
| `src/graphql/client.ts` | `isPaymentUnavailable`, `unlockLimit` and the `retryAfter` extension removed |
| `src/graphql/documents.ts` | `LostItemClaimQuery`, `FoundItemClaimQuery`, `UnlockLostItemClaimMutation`, `UnlockFoundItemClaimMutation` removed; `ClaimLostItem` / `ClaimFoundItem` ask only for `id` and `repeated` |
| `src/graphql/generated/*` | regenerated (`pnpm codegen`) |
| `src/schema/claim-schema.ts` (+ test) | `waiverSchema`, `WaiverValues`, `CLAIM_ID` removed |
| `src/app/[locale]/playground/` | `fourthwall-bench.tsx`, `fourthwall-webhook.ts` (+ test), `actions.ts` deleted; `page.tsx` no longer renders the bench |
| `messages/*.json` | `claim.unlock.*` and `form.error.waiver` removed; `home.benefit2Text`, `home.benefit3Text`, `safety.metaDescription`, `safety.protect.contacts.text`, `safety.protect.limit.text` reworded without the paid number and the weekly limit |
| `src/content/legal/*.json` | terms: section 5 "The author's phone number" (`id: author-number`) removed and the later sections renumbered, section 4 reworded; privacy: the Fourthwall order data, payment purposes and legal basis, Fourthwall as a processor and its order retention removed, "what others can see" and the `DERECHI_CLAIM_*` cookie reworded |
| `src/content/legal/jurisdictions/*.json`, `types.ts` | the `withdrawalLaw` slot removed; `updated` set to 2026-10-10 |
| `README.md` | the "Unlocking the author's phone number" section replaced by a pointer here; SMS outage and legal sections updated |

Kept on purpose: `fromIsoInstant` (`src/lib/intl/dates.ts`, a general helper with its own test),
`readClaimCookie`, the 30-day `DERECHI_CLAIM_*` cookie holding the claim id (so the restored
dialog finds existing claims again), `useSmsOutageNotice`, `ConsentCheckbox`, `LinkButton`.

## How to bring it back

1. Move the site to a hosting plan that allows commercial use (on Vercel: Pro), **before** the
   restored code is deployed.
2. Revert the commit that removed it. Besides the code it also takes back the "Switched off" note
   in [Claims](claims.md), the line in the [features index](README.md) and the changes in
   `Web-Client/README.md`.

   ```bash
   git show --stat 26f3f08            # what the commit changed
   git revert 26f3f08                 # restore everything, as a new commit
   ```

   If later commits touched the same lines, `git revert` stops on the conflicts; resolve them,
   then `git revert --continue`. To bring back only part of it, take files from the last commit
   that had the feature, e.g.
   `git checkout cc4f0fc -- Web-Client/src/screens/found_lost/claim/claim-unlock.tsx`. That is
   safe for the deleted files; for edited ones it also drops anything changed since.
3. Regenerate the GraphQL types and check the client:

   ```bash
   cd Web-Client
   pnpm codegen
   pnpm typecheck && pnpm lint && pnpm test && pnpm build
   ```

4. Legal texts: set `updated` in every `src/content/legal/jurisdictions/*.json` to the day the
   texts go live again, and have the restored terms section 5 and the privacy sections checked
   against the current Fourthwall setup and prices.
5. Delete this page, or mark it historical.
6. Check by hand on a deployed preview: respond to a notice, open "Get the author's phone
   number", tick the waiver, and pay with the playground bench locally
   (`/<locale>/playground`, `FOURTHWALL_WEBHOOK_SECRET`), or with a real $1 order in the
   shop. The dialog has to switch to "payment received" and then "sent".
