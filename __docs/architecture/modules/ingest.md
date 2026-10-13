# Ingest

Imports lost-and-found notices from outside sources and publishes them. The sources are Telegram (a userbot session reads the chats of one Telegram folder) and Facebook groups (read through a logged-in account once an hour); a model through OpenRouter extracts the notice, and the result goes to the publishers in `INGEST_PUBLISHERS`: the site (Client-API's public GraphQL), our Telegram channel, the review chat. What it does step by step is in [the feature page](../../features/telegram-ingest.md).

| | |
|---|---|
| Port | 8086 (serves nothing public); actuator on the management port 9086 |
| Consumes | Telegram updates (TDLib through TDLight); Facebook group pages and the group feed query of `www.facebook.com`, polled; no queue |
| Produces | notices through the API entry, Caddy for now (`createLostItem` / `createFoundItem`), photos in MinIO, posts in our Telegram channel and the review chat (Bot API) |
| Needs | PostgreSQL (`ingest_post`), the API entry (Caddy) with Client-API behind it, MinIO; OpenRouter, Google Places, Telegram, Facebook |
| Stack | TDLight Java (TDLib 1.8.66), libphonenumber, Tess4J + system Tesseract, ONNX Runtime + YuNet, Spring for GraphQL client, Spring Cloud AWS S3, Lombok |
| Instances | exactly one: the TDLib session cannot be shared |

## Packages

One package per stage. The root of a stage package is what the pipeline sees: the interface and its records. The implementation that knows an outside service sits one level down, named after that service (`source/telegram/`, `extraction/openrouter/`, `place/google/`); another provider is another subpackage next to it. `config/`, `entity/`, `repository/` and `exception/` stay at the top, as in the other modules; `config/` keeps only what the whole module uses (`IngestConfig`, `IngestProperties`) and has a subpackage per stage for the rest. Tests mirror the main packages.

| Package | Classes |
|---|---|
| `source/` | `PostSource`, `PostSources` (all sources by name), `IncomingPost`, `PostKey`, `SourceSettings`, `PhotoRef`, `SourceDetails` |
| `source/telegram/` | `TelegramPostSource` (the `PostSource` named `telegram`) over `TelegramSource` / `TdlightTelegramSource`, `MessageMapper`, `PostBuffer` and `PostGroup` (an album or a text with its photos sent separately become one post), `AuthorPhoneResolver`, `TelegramDetails`, `ChatSettingsParser`, `TelegramLogin` (`--login`) |
| `source/facebook/` | `FacebookPostSource` (the `PostSource` named `facebook`, polls the groups), `FacebookGroupReader` (the group page, then the feed query page by page), `FacebookParser`, `FacebookClient` (the account's cookies) |
| `phone/` | `PhoneExtractor`, `Masker`, `PhoneHasher` |
| `image/` | `ImageRedactor`, `RedactedImage`, `RedactionMode` |
| `image/redaction/` | `DefaultImageRedactor` (what to blur), `DocumentDetector`, `Blur` |
| `image/ocr/` | `OcrEngine`, `OcrLine`, `TesseractOcrEngine` |
| `image/face/` | `FaceDetector`, `YuNetFaceDetector` |
| `extraction/` | `Extractor`, `ExtractionRequest`, `Extraction` (the model's answer), `ExtractionResult`, `ExtractionPolicy` (is the answer a usable notice, which phone to take), `PromptBuilder` (`prompts/extract.md`, `prompts/extraction-schema.json`) |
| `extraction/openrouter/` | `OpenRouterExtractor`, `CompletionRequest`, `DailyCallCounter` |
| `place/` | `PlaceResolver`, `ResolvedPlace` |
| `place/google/` | `GoogleLegacyPlaceResolver` (`GOOGLE_PLACES_API=LEGACY`, default) or `GooglePlaceResolver` (`NEW`), both on `TextSearchPlaceResolver` |
| `pipeline/` | `IngestQueue`, `IngestPipeline`, `PostLedger`, `IngestMetrics`, `Notice`, `Notices` |
| `publish/` | `Publisher`, `Publishers` (runs the list of `INGEST_PUBLISHERS`), `Publication`, `SiteItem`, `SkippedPost` |
| `publish/site/` | `SitePublisher` (`site`), `ImageUploader` / `S3ImageUploader` (MinIO) |
| `publish/telegram/` | `ChannelPoster` (`channel`), `ReviewChat` (`review`), `BotApiClient`, `CaptionBuilder`, `ChannelPost` |
| `client/` | `DeRechiApi` / `GraphQlDeRechiApi` and `NewItem`: Client-API, used by the pipeline (categories, countries) and by `site`; `HttpCalls` for every HTTP client |
| `model/` | `ItemKind`, `Money`, `SocialMedia`, `Outcome`: the values shared by the model's answer, the notice, the ledger, the publishers and the metrics |

## Sources

A post enters as an `IncomingPost`: a `PostKey` (source name, channel, id within the channel; the unique key of `ingest_post`), the source title and link, the text, `PhotoRef`s, the `SourceSettings` of its channel (region, default city, locale) and `SourceDetails` only its source understands (`TelegramDetails`: chat and message ids, the author, mentions). The pipeline asks the post's source, found by `PostSources` by name, to download a photo and to look for the author's phone when the text has none.

### Facebook

`FacebookPostSource` reads the groups of `FACEBOOK_GROUPS` (`group=city[|region[|locale]]`, the group is its name or id from the URL) through a logged-in account whose browser cookies are in `FACEBOOK_COOKIES`. Facebook has no API for groups since 2024, so this is the web client's own traffic, and it breaks Meta's terms: use a separate account, expect it to be banned one day.

- A poll starts a minute after the start and then every `FACEBOOK_POLL_EVERY` plus a random part of `FACEBOOK_POLL_JITTER`; between two requests it waits a random 0.5–1.5 × `FACEBOOK_REQUEST_PAUSE`.
- For each group: `GET /groups/<group>/?sorting_setting=CHRONOLOGICAL`. The page carries the session tokens (`USER_ID`, `fb_dtsg`, `lsd`), the variables of the feed query, the newest post and the feed cursor, all in `<script type="application/json">` blobs. `USER_ID` 0 means the cookies no longer log in: the group is skipped and `derechi.ingest.facebook.authorized` drops to 0.
- Older posts come from `POST /api/graphql/` with the persisted query `GroupsCometFeedRegularStoriesPaginationQuery`, three posts and a new cursor per answer (a JSON line per post; `field_exception` errors in it are partial and ignored). Its `doc_id` changes with Facebook's releases: the reader finds it in the JavaScript bundles the page links (`<Query>_facebookRelayOperation`), caches it and looks it up again when the query fails.
- Paging stops at a post older than the previous poll's start minus 30 minutes (`FACEBOOK_CATCH_UP_MAX_AGE` back on the first poll), at the end of the feed or after `FACEBOOK_MAX_PAGES`. Posts are handed over oldest first, keyed `facebook / <group> / <post_id>`, linked to the permalink; repeats are dropped by the ledger.
- Photos are the `viewer_image` of the attachments, downloaded from the CDN by URL. There is no author phone: a post without a number in its text is dropped.

A new source (a website, say) is one `@Component` implementing `PostSource` with its own name, in its own subpackage of `source/`, plus its `SourceDetails` if it needs any. It turns what it reads into `IncomingPost`s and hands them to the sink given to `start`; the queue, the ledger, the duplicate check and everything after are shared. `authorPhone` returns empty when the source has no such notion.

## Publishers

`INGEST_PUBLISHERS` lists where a notice goes, in any order: `site`, `channel`, `review`.

- `site` (`SitePublisher`) is special: it runs first, puts the photo into MinIO, creates the notice through the API entry and owns the ledger statuses `PUBLISHING`, `PUBLISHED`, `NEEDS_CHECK`, `FAILED`. Without `site` in the list a notice is recorded as `PREVIEWED`.
- Every other publisher implements `Publisher` and gets a `Publication`: the post, the notice, the model answer, the redacted photos and the `SiteItem` (id and page URL) when `site` created one. If `site` is in the list and fails, nobody else is called.
- A publisher whose `needsSiteItem()` is true (`channel`) links to the notice page instead of carrying contacts; the start fails if it is listed without `site`.
- Publishers are independent: a failing one is logged (`WARN`) and counted in `derechi.ingest.published{publisher,outcome}`, the others still run, and the ledger status stays what `site` set. Skipped posts go to `Publisher.skipped`, which only `review` uses.

Each publisher turns the `Notice` into its own format (`SitePublisher` into Client-API's `NewItem`, `ChannelPost.of` into a channel post), so the pipeline knows none of them.

A new publisher (a Viber group, say) is one `@Component` implementing `Publisher` with its own name, in its own subpackage of `publish/`; adding the name to `INGEST_PUBLISHERS` turns it on. An unknown name fails the start.

## Build and image

- TDLight is not on Maven Central: `Ingest/pom.xml` declares `https://mvn.mchv.eu/repository/mchv/`. Versions are pinned in the root `<dependencyManagement>` without importing TDLight's BOM, because an imported BOM would be resolved for every module.
- The TDLib native comes from an OS-activated Maven profile: the jar carries the native of the machine that builds it. The image is built on the machine it runs on (arm64 on OCI), so that is the right one.
- The root `Dockerfile` has a final stage of its own, `ingest` (compose `build.target`): it adds the Tesseract packages, sets `TESSDATA_PREFIX`, creates `/app/tdlib` for the session volume, and its entrypoint forwards arguments for `--login`. See [docker-image](../../deployment/docker-image.md).
- `JAVA_OPTS` needs `--enable-native-access=ALL-UNNAMED`: TDLight loads its native with `System.loadLibrary`, which JDK 26 warns about and later releases will block.
- The TDLib device model stays `DeRechi Telegram-Ingest`, the module's old name, so the existing session does not show up as a new device.

## Configuration

`derechi.telegram.*`, `derechi.bot.*`, `derechi.ingest.*`, `derechi.openrouter.*`, `derechi.places.*`, `derechi.api.*`, `derechi.storage.*`, `derechi.redaction.*`, one record each: `IngestProperties` in `config/`, the others in `config/telegram/`, `config/bot/`, `config/openrouter/`, `config/places/`, `config/client/`, `config/storage/`, `config/redaction/`, each with the `@Configuration` that builds its client where there is one. Every key has an environment variable; the list is in [environment variables](../../deployment/environment-variables.md). Missing keys do not fail the start: the stage reports itself unavailable and the post waits for the next catch-up. An unknown name in `INGEST_PUBLISHERS`, or `channel` without `site`, does fail it.

## Tests

Unit tests per stage (`*Test`), HTTP clients against `MockRestServiceServer`, `PostLedgerTests` and `IngestApplicationTests` on the Testcontainers database with `ddl-auto=validate`. Nothing talks to Telegram, OpenRouter or Google; `TesseractOcrEngineIT` runs only when `TESSDATA_PREFIX` is set. `support/Posts` and `support/Outputs` build the shared test posts and notices, `support/Images` the noise images and the variance check of the redaction tests.
