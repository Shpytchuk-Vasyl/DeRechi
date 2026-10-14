# Telegram import

The `Ingest` module reads the lost-and-found channels and groups our Telegram account follows, turns real notices into site notices and reposts them to our own channel. Public Facebook groups are read too, through a logged-in account once an hour (see [Ingest](../architecture/modules/ingest.md#facebook)); from the phone step on, a Facebook post goes the same way. Notices created this way are ordinary notices: matching in `Worker` and claims work for them unchanged.

The design and the decisions behind it are in [the spec](../specs/telegram-ingest.md) until the feature has run in production for a while; this page describes what the code does.

## The flow

One post at a time, from an in-memory queue (`IngestQueue` → `IngestPipeline`):

1. **Listen.** `TdlightTelegramSource` (TDLight, a userbot session) takes every new text or photo message of the chats in the Telegram folder `TELEGRAM_FOLDER`. Messages of one sender (the forward origin, else the sender) that come within `TELEGRAM_ALBUM_WAIT` of each other and carry at most one text are merged into one post (`PostBuffer`, `PostGroup`): an album, but also a text with its photos sent as a separate message or album, which is how channel admins forward a person's notice. Each message therefore waits `TELEGRAM_ALBUM_WAIT` before it goes on; the merged post is keyed by its first message. The catch-up groups the history the same way. After a start it also replays the last `TELEGRAM_CATCH_UP_MAX_AGE` of every chat; posts already in `ingest_post` are dropped. `FacebookPostSource` polls the groups of `FACEBOOK_GROUPS` every `FACEBOOK_POLL_EVERY` plus a random jitter and hands over what is new since the last poll.
2. **Phone.** `PhoneExtractor` (libphonenumber, the chat's region) finds numbers in the text. No number in the text → the post's source looks for the author's phone; for Telegram `AuthorPhoneResolver` tries the phone the author made public in their Telegram profile: the sender in a group, the forward origin of a channel post, then users mentioned in the text (`@username` or a link to the user), because channel posts often name the author only as `Контакт: @username`. Still nothing → the post is dropped without a trace (`outcome=no_phone`).
3. **Duplicate.** The same contact number (HMAC in `ingest_post.phone_hash`) on a notice within `INGEST_DUPLICATE_WINDOW` → skipped. This runs before the model, so a repost in five channels costs one model call.
4. **Mask.** `Masker` replaces numbers with `[PHONE_n]` and emails with `[EMAIL_n]`; the mapping stays in memory. No phone or email leaves the machine.
5. **Redact photos.** Up to `INGEST_MAX_PHOTOS` photos are downloaded and redacted by `DefaultImageRedactor` before anything else sees them: Tesseract (`ukr+eng`) finds lines with phone numbers or emails and blurs them; a bank card number is blurred up to its last 4 digits; a document (keywords in the post or on the photo, an MRZ line, or the largest face near the left edge, found by YuNet through ONNX Runtime) is blurred everywhere except its faces, so the owner or relatives can recognize the person, and entirely when it has no face. Every photo is re-encoded, which drops EXIF and GPS data.
6. **Extract.** `OpenRouterExtractor` sends the masked text and the redacted photos to OpenRouter with a strict JSON schema (`prompts/extraction-schema.json`, the categories of Client-API patched in). `ExtractionPolicy` decides whether the answer is a usable notice and picks the contact number by the placeholder the model returned.
7. **Place.** Google Places Text Search for the place text with the city or the chat's default city, then for the city alone. `GOOGLE_PLACES_API` picks the API: `LEGACY` (default, `GoogleLegacyPlaceResolver`, the country comes from the end of `formatted_address`, else the chat's region) or `NEW` (`GooglePlaceResolver`, Places API (New) with a field mask). Google's name of a place is a venue or a street ("MAGNUS", "проспект Свободи, 92"), so the stored name is Google's address, with the venue name in front unless the address already starts with it ("Магнус, вулиця Зелена, 100, Львів, Львівська область"): the formatted address (`formatted_address` with `LEGACY`, `formattedAddress` in the field mask with `NEW`). The country and everything after it (the postal code) is cut off, as the country is stored in its own field. The whole name is cut to 100 characters. The model writes `placeText` as a map search query (official names instead of local slang, using the chat's city as `chatCity`). A failed search (an HTTP error or a non-`OK` status) skips the post as `no_place` with a `WARN`, it does not fail it.
8. **Publish.** The pipeline hands the ready notice (or the skip) to `Publishers`, which runs every publisher named in `INGEST_PUBLISHERS`:
   - `site` (`SitePublisher`) goes first: it puts the photo into MinIO (`S3ImageUploader`) and creates the notice through the public API entry, Caddy for now (`GraphQlDeRechiApi`, `createLostItem` / `createFoundItem`). Without `site` the post is recorded as previewed and nothing reaches the site.
   - `channel` (`ChannelPoster`, Bot API) posts to our channel. Its caption never carries contacts; it links to the notice page, where a reader leaves their contacts through a claim, so `channel` needs `site` in the list.
   - `review` (`ReviewChat`) sends every notice and every skip to the review chat. A preview with a photo looks like the channel post (the photo with its caption), and the model's JSON follows as a separate message, since it does not fit a 1024-character caption. The header names the outcome: `previewed`, `published` or the skip reason.

   `INGEST_PUBLISHERS=review` is a dry run, `site,channel` publishes, `site,channel,review` publishes and keeps a copy of everything in the review chat. If `site` fails, nobody else is called; any other publisher failing only logs a `WARN`.

## `ingest_post`

One row per post that reached the model (migration `012`), keyed by source, channel and the post's id in it. No text, no phone number.

| Status | Meaning |
|---|---|
| `SKIPPED` | not published; the reason is the `outcome` tag of `derechi.ingest.posts` and the log line |
| `PREVIEWED` | `site` is not in `INGEST_PUBLISHERS`: the other publishers got the notice |
| `PUBLISHING` | committed right before the GraphQL call |
| `PUBLISHED` | the notice exists on the site; whether the channel post and the other publishers succeeded is in the log and `derechi.ingest.published` |
| `FAILED` | retries exhausted. Delete the row and the next start's catch-up takes the post again, if it is younger than the catch-up window |
| `NEEDS_CHECK` | found in `PUBLISHING` after a crash, or the GraphQL call ended unclearly: check on the site whether the notice exists. Never retried automatically |

A stage that is not configured (no OpenRouter or Places key, Client-API unreachable) records nothing (`outcome=unavailable`), so the post comes back with the next catch-up once the stage works. A post over `OPENROUTER_DAILY_LIMIT` records nothing either (`outcome=budget`): `IngestQueue` keeps it aside (`derechi.ingest.postponed`) and queues it again when the limit resets at the next UTC midnight; the same post arriving meanwhile is not queued twice. A restart forgets the postponed posts, but the catch-up brings them back, since the ledger has no row for them.

## Running it

- Telegram session, once per volume: stop the service (`docker compose ... stop ingest`: TDLib locks its database folder), run `docker compose -f docker-compose.yml -f docker-compose.services.yml run --rm -it ingest --login`, which asks for the code and the 2FA password and stores the session in the `tdlib-data` volume, then start the service again. Use a separate account, not a personal one.
- Without `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` the service starts and does nothing; without a session it starts, logs one error and `IngestUnauthorized` fires.
- Per-chat settings come from one variable: `TELEGRAM_CHATS=zagubleno911=Львів;other_chat=Kraków|PL|pl` (`username=city[|region[|locale]]`). A Lviv channel whose posts name only a crossroads needs its city here, or Places will not find the place.
- Start with `INGEST_PUBLISHERS=review` and `TELEGRAM_REVIEW_CHAT` set, read the previews, then switch to `site,channel` (or `site,channel,review` to keep watching).
- To see where a post is stuck, set `LOGGING_LEVEL_ORG_SHPYTCHUK_INGEST_PIPELINE=DEBUG`: the pipeline then logs every step with its duration (queued, phone, duplicate check, each photo's download and redaction, the model answer, the checks, the place search, the upload). The last line of a post names the last finished step; the next one is the slow one. Every external call has a timeout (TDLib 30 s, model `OPENROUTER_TIMEOUT` per attempt, Places, Client-API), OCR does not.
- Exactly one instance: the TDLib session cannot be shared.
- Facebook: log in to a separate account in a browser, open `www.facebook.com`, copy the `Cookie` request header (DevTools, Network) into `FACEBOOK_COOKIES`, set `FACEBOOK_USER_AGENT` to that browser's user agent and list the groups in `FACEBOOK_GROUPS=zzter=Тернопіль;...`. The cookies are a password: `env.test` / `env.prod` only. Without them the source logs one warning and reads nothing; when they stop logging in, `FacebookUnauthorized` fires and they have to be copied again.

Every variable is in [environment variables](../deployment/environment-variables.md).

## Metrics and alerts

`derechi.ingest.posts{outcome}`, `derechi.ingest.phone.source{source}`, `derechi.ingest.llm.duration{model}`, `derechi.ingest.llm.cost{model}` (USD), `derechi.ingest.image.redacted{mode}`, `derechi.ingest.published{publisher,outcome}`, `derechi.ingest.queue`, `derechi.ingest.postponed`, `derechi.ingest.telegram.authorized`, `derechi.ingest.facebook.authorized`, `derechi.ingest.facebook.polls{group,outcome}`, `derechi.ingest.last.received` (epoch seconds). Alerts `IngestUnauthorized`, `FacebookUnauthorized` and `IngestStalled`, see [monitoring](../deployment/monitoring.md).

## Known gaps

- Edits and deletions of the source post are not followed.
- A notice needs a phone number: a post with only a Telegram username whose owner hides the phone is dropped, although the person is reachable. Client-API requires a phone today.
- OCR deskews only a rotation of 0.5° to 20° and does not correct perspective: a phone number or card number on a sharply angled photo can stay unblurred. A document is still caught by its portrait.
- Outside documents faces are not blurred.
- WebP photos are not decoded (the JDK has no WebP reader); Telegram serves photos as JPEG.
- The in-memory queue and the username cache are lost on restart (the catch-up refills the queue).
- A publisher that failed after `site` succeeded (`channel`, say) is not retried; the channel post has to be made by hand.
- Facebook is read through the web client's undocumented feed query: a change on their side shows up as `derechi.ingest.facebook.polls{outcome="failed"}` until the reader is fixed. The account can be banned, and a datacenter IP meets login walls sooner than a home one. Facebook posts have no author phone, so a post without a number in its text is dropped.

## Where to look

- `Ingest/src/main/java/org/shpytchuk/ingest/pipeline/` (`IngestPipeline`, `IngestQueue`, `PostLedger`, `IngestMetrics`)
- next to it one package per stage: `source/` (`telegram/`), `phone/`, `image/`, `extraction/` (`openrouter/`), `place/` (`google/`), `publish/` (`site/`, `telegram/`); `client/` is Client-API, `model/` the shared values. See [the module page](../architecture/modules/ingest.md#packages)
- `Ingest/src/main/resources/prompts/extract.md`, `prompts/extraction-schema.json`, `messages*.properties`
- `DB-Postgres/changelog/changes/012-ingest-post.postgresql.sql`
