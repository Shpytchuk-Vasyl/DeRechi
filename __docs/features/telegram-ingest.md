# Telegram import

`Telegram-Ingest` reads the lost-and-found channels and groups our Telegram account follows, turns real notices into site notices and reposts them to our own channel. Notices created this way are ordinary notices: matching in `Worker` and claims work for them unchanged.

The design and the decisions behind it are in [the spec](../specs/telegram-ingest.md) until the feature has run in production for a while; this page describes what the code does.

## The flow

One post at a time, from an in-memory queue (`IngestQueue` → `IngestPipeline`):

1. **Listen.** `TdlightTelegramSource` (TDLight, a userbot session) takes every new text or photo message of the chats in the Telegram folder `TELEGRAM_FOLDER`. Albums are merged into one post (`AlbumBuffer`, `TELEGRAM_ALBUM_WAIT`). After a start it also replays the last `TELEGRAM_CATCH_UP_MAX_AGE` of every chat; posts already in `telegram_post` are dropped.
2. **Phone.** `PhoneExtractor` (libphonenumber, the chat's region) finds numbers in the text. No number in the text → `AuthorPhoneResolver` tries the phone the author made public in their Telegram profile: the sender in a group, the forward origin of a channel post, then users mentioned in the text (`@username` or a link to the user), because channel posts often name the author only as `Контакт: @username`. Still nothing → the post is dropped without a trace (`outcome=no_phone`).
3. **Duplicate.** The same contact number (HMAC in `telegram_post.phone_hash`) on a notice within `INGEST_DUPLICATE_WINDOW` → skipped. This runs before the model, so a repost in five channels costs one model call.
4. **Mask.** `Masker` replaces numbers with `[PHONE_n]` and emails with `[EMAIL_n]`; the mapping stays in memory. No phone or email leaves the machine.
5. **Redact photos.** Up to `INGEST_MAX_PHOTOS` photos are downloaded and redacted by `DefaultImageRedactor` before anything else sees them: Tesseract (`ukr+eng`) finds lines with phone numbers or emails and blurs them; a bank card number is blurred up to its last 4 digits; a document (keywords in the post or on the photo, an MRZ line, or the largest face near the left edge, found by YuNet through ONNX Runtime) is blurred everywhere except its faces, so the owner or relatives can recognize the person, and entirely when it has no face. Every photo is re-encoded, which drops EXIF and GPS data.
6. **Extract.** `OpenRouterExtractor` sends the masked text and the redacted photos to OpenRouter with a strict JSON schema (`llm/extraction-schema.json`, the categories of Client-API patched in). `ExtractionPolicy` decides whether the answer is a usable notice and picks the contact number by the placeholder the model returned.
7. **Place.** `GooglePlaceResolver` runs Places API (New) Text Search for the place text with the city or the chat's default city, then for the city alone.
8. **Publish.** The pipeline hands the ready notice (or the skip) to the `NoticeOutput` of the mode; exactly one exists, chosen by `INGEST_MODE`. `PreviewOutput` (`DRY_RUN`) records the post as previewed and sends a preview to the review chat (`ReviewChat`), skips included; nothing is published. `PublishingOutput` (`AUTO`) puts the photo into MinIO (`S3ImageUploader`), creates the notice through the Gateway (`GraphQlDeRechiApi`, `createLostItem` / `createFoundItem`), then posts to our channel (`ChannelPoster`, Bot API). The channel caption never carries contacts; it links to the notice page, where a reader leaves their contacts through a claim.

## `telegram_post`

One row per post that reached the model (migration `012`). No text, no phone number.

| Status | Meaning |
|---|---|
| `SKIPPED` | not published; the reason is the `outcome` tag of `derechi.ingest.posts` and the log line |
| `PREVIEWED` | `DRY_RUN`: the preview was sent |
| `PUBLISHING` | committed right before the GraphQL call |
| `PUBLISHED` | the notice exists, the channel post does not (yet) |
| `DONE` | on the site and in the channel |
| `FAILED` | retries exhausted. Delete the row and the next start's catch-up takes the post again, if it is younger than the catch-up window |
| `NEEDS_CHECK` | found in `PUBLISHING` after a crash, or the GraphQL call ended unclearly: check on the site whether the notice exists. Never retried automatically |

A stage that is not configured (no OpenRouter or Places key, Client-API unreachable) records nothing (`outcome=unavailable`), so the post comes back with the next catch-up once the stage works.

## Running it

- Telegram session, once per volume: stop the service (`docker compose ... stop telegram-ingest`: TDLib locks its database folder), run `docker compose -f docker-compose.yml -f docker-compose.services.yml run --rm -it telegram-ingest --login`, which asks for the code and the 2FA password and stores the session in the `tdlib-data` volume, then start the service again. Use a separate account, not a personal one.
- Without `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` the service starts and does nothing; without a session it starts, logs one error and `IngestUnauthorized` fires.
- Per-chat settings come from one variable: `TELEGRAM_CHATS=zagubleno911=Львів;other_chat=Kraków|PL|pl` (`username=city[|region[|locale]]`). A Lviv channel whose posts name only a crossroads needs its city here, or Places will not find the place.
- Start in `INGEST_MODE=DRY_RUN` with `TELEGRAM_REVIEW_CHAT` set, read the previews, then switch to `AUTO`.
- Exactly one instance: the TDLib session cannot be shared.

Every variable is in [environment variables](../deployment/environment-variables.md).

## Metrics and alerts

`derechi.ingest.posts{outcome}`, `derechi.ingest.phone.source{source}`, `derechi.ingest.llm.duration{model}`, `derechi.ingest.llm.cost{model}` (USD), `derechi.ingest.image.redacted{mode}`, `derechi.ingest.queue`, `derechi.ingest.telegram.authorized`, `derechi.ingest.last.received` (epoch seconds). Alerts `IngestUnauthorized` and `IngestStalled`, see [monitoring](../deployment/monitoring.md).

## Known gaps

- Edits and deletions of the source post are not followed.
- A notice needs a phone number: a post with only a Telegram username whose owner hides the phone is dropped, although the person is reachable. Client-API requires a phone today.
- OCR deskews only a rotation of 0.5° to 20° and does not correct perspective: a phone number or card number on a sharply angled photo can stay unblurred. A document is still caught by its portrait.
- Outside documents faces are not blurred.
- WebP photos are not decoded (the JDK has no WebP reader); Telegram serves photos as JPEG.
- The in-memory queue and the username cache are lost on restart (the catch-up refills the queue).
- `PUBLISHED` rows (site yes, channel no) are not retried; the channel post has to be made by hand.

## Where to look

- `Telegram-Ingest/src/main/java/org/shpytchuk/telegramingest/pipeline/` (`IngestPipeline`, `IngestQueue`, `PostLedger`, `IngestMetrics`)
- `telegram/`, `phone/`, `image/`, `llm/`, `geo/`, `api/`, `storage/`, `channel/` next to it, one package per stage
- `Telegram-Ingest/src/main/resources/prompts/extract.md`, `llm/extraction-schema.json`, `messages*.properties`
- `DB-Postgres/changelog/changes/012-telegram-ingest.postgresql.sql`
