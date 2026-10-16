# Ingest

Imports lost-and-found notices from outside sources and publishes them. The sources are Telegram (a userbot session reads the chats of one Telegram folder) and Facebook groups (read through a logged-in account once an hour); a model through OpenRouter extracts the notice, and the result goes to the publishers in `INGEST_PUBLISHERS`: the site (Client-API's public GraphQL), our Telegram channel. It also listens to `ingest.queue` and mirrors the notices created on the site and in the admin panel into the channel, deleting a post when its notice is archived or deleted. What it does step by step is in [the feature page](../../features/telegram-ingest.md).

| | |
|---|---|
| Port | 8086 (serves nothing public); actuator on the management port 9086 |
| Consumes | Telegram updates (TDLib through TDLight); Facebook group pages and the group feed query of `www.facebook.com`, polled; `ingest.queue` (`notice.*.created`, `notice.*.archived`, `notice.*.deleted` from `notice.status.exchange`) |
| Produces | notices through the API entry, Caddy for now (`createLostItem` / `createFoundItem`), photos in MinIO, posts in our Telegram channel (Bot API), deletions of channel posts; no events |
| Needs | PostgreSQL (`ingest_post`), RabbitMQ, the API entry (Caddy) with Client-API behind it, MinIO; OpenRouter, Google Places, Telegram, Facebook |
| Stack | TDLight Java (TDLib 1.8.66), libphonenumber, Tess4J + system Tesseract, ONNX Runtime + YuNet, Spring for GraphQL client, Spring Cloud AWS S3, Lombok |
| Instances | exactly one: the TDLib session cannot be shared |

## Packages

One package per stage. The root of a stage package is what the pipeline sees: the interface and its records. The implementation that knows an outside service sits one level down, named after that service (`source/telegram/`, `extraction/openrouter/`, `place/google/`); another provider is another subpackage next to it. `config/`, `entity/`, `repository/` and `exception/` stay at the top, as in the other modules; `config/` keeps only what the whole module uses (`IngestConfig`, `IngestProperties`) and has a subpackage per stage for the rest. Tests mirror the main packages.

| Package | Classes |
|---|---|
| `source/` | `PostSource`, `PostSources` (all sources by name), `IncomingPost`, `PostKey`, `SourceSettings`, `PhotoRef`, `SourceDetails`, `AuthorProfile` (the author's nickname and profile link in one network) |
| `source/telegram/` | `TelegramPostSource` (the `PostSource` named `telegram`) over `TelegramSource` / `TdlightTelegramSource`, `MessageMapper`, `PostBuffer` and `PostGroup` (an album or a text with its photos sent separately become one post), `AuthorPhoneResolver`, `AuthorProfileResolver`, `TelegramDetails`, `ChatSettingsParser`, `TelegramLogin` (`--login`) |
| `source/facebook/` | `FacebookPostSource` (the `PostSource` named `facebook`, polls the groups), `FacebookGroupReader` (the group page, then the feed query page by page), `FacebookParser`, `FacebookClient` (the account's cookies), `FacebookDetails` / `FacebookActor` (the post's author) |
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
| `publish/` | `Publisher`, `Publishers` (runs the list of `INGEST_PUBLISHERS`), `Publication`, `SiteItem` |
| `publish/site/` | `SitePublisher` (`site`), `ImageUploader` / `S3ImageUploader` (MinIO) |
| `publish/telegram/` | `ChannelPoster` (`channel`; `postFromSite` and `delete` for one notice), `ChannelMirror` (notices from the site), `StoredPhotos` (a photo from the bucket by key), `BotApiClient`, `CaptionBuilder`, `ChannelPost` |
| `event/` | `NoticeEventListener` (`ingest.queue`), `ItemCreatedEvent`, `ItemArchivedEvent`, `ItemDeletedEvent`, `EventType`, `EventTypeScanner`: copied per module like in the other consumers |
| `client/` | `DeRechiApi` / `GraphQlDeRechiApi` and `NewItem`: Client-API, used by the pipeline (categories, countries) and by `site`; `HttpCalls` for every HTTP client |
| `model/` | `ItemKind`, `Money`, `SocialMedia`, `Outcome`: the values shared by the model's answer, the notice, the ledger, the publishers and the metrics |

## Sources

A post enters as an `IncomingPost`: a `PostKey` (source name, channel, id within the channel; the unique key of `ingest_post`), the source title and link, the text, `PhotoRef`s, the `SourceSettings` of its channel (region, default city, locale) and `SourceDetails` only its source understands (`TelegramDetails`: chat and message ids, the author, mentions). The pipeline asks the post's source, found by `PostSources` by name, to download a photo, to look for the author's phone when the text has none, and failing that for the author's profile (`authorProfile`).

### Facebook

`FacebookPostSource` reads the groups of `FACEBOOK_GROUPS` (`group=city[|region[|locale]]`, the group is its name or id from the URL) through a logged-in account whose browser cookies are in `FACEBOOK_COOKIES`. Facebook has no API for groups since 2024, so this is the web client's own traffic, and it breaks Meta's terms: use a separate account, expect it to be banned one day.

- A poll starts a minute after the start and then every `FACEBOOK_POLL_EVERY` plus a random part of `FACEBOOK_POLL_JITTER`; between two requests it waits a random 0.5–1.5 × `FACEBOOK_REQUEST_PAUSE`.
- `FACEBOOK_ACTIVE_HOURS` (`07:00-22:00`, empty for all day) keeps the account quiet at night, as a person would be: a group is read only while its local time (the zone of its region, `SourceSettings.zone()`) is inside the range. When no group is, the poll makes no request and the next one is set for the earliest opening plus a random part of `FACEBOOK_POLL_JITTER`, not for `FACEBOOK_POLL_EVERY` later. The night's posts come with the first morning poll, which pages back to the previous evening's read (up to `FACEBOOK_MAX_PAGES`).
- For each group: `GET /groups/<group>/?sorting_setting=CHRONOLOGICAL`. The page carries the session tokens (`USER_ID`, `fb_dtsg`, `lsd`), the variables of the feed query, the newest post and the feed cursor, all in `<script type="application/json">` blobs. `USER_ID` 0 (or none) means the cookies no longer log in: it is checked before anything else, so a login wall without a feed counts too, the group is skipped and `derechi.ingest.facebook.authorized` drops to 0. Facebook may also hold the whole account behind a page that wants an action in a browser. The group page is fetched without following redirects automatically (`FacebookClient.page` follows up to 5 itself), so a redirect to `/privacy/consent/` (the cookie choice Facebook asks for in the EU, met when the server sits in an EU region) or to `/checkpoint/` is recognised by its address; a logged-in page without the feed query or the `lsd` token that carries `"is_checkpointed":true` (every page has the flag, `false` normally) counts as a checkpoint too. Then the poll stops at that group without touching the others until the next poll (each request to a held account makes it worse), the outcome is `action_required` and `derechi.ingest.facebook.authorized` drops to 0. For the cookie choice, open Facebook with that account in a browser going out through the server's network (an SSH SOCKS proxy, `ssh -D`), choose, and refresh `FACEBOOK_COOKIES`. Any other page without the feed (a changed markup) fails with the page's `<title>` in the message, and the other groups are still read.
- Older posts come from `POST /api/graphql/` with the persisted query `GroupsCometFeedRegularStoriesPaginationQuery`, three posts and a new cursor per answer (a JSON line per post; `field_exception` errors in it are partial and ignored). Its `doc_id` changes with Facebook's releases: the reader finds it in the JavaScript bundles the page links (`<Query>_facebookRelayOperation`), caches it and looks it up again when the query fails.
- Paging stops at a post older than the previous poll's start minus 30 minutes (`FACEBOOK_CATCH_UP_MAX_AGE` back on the first poll), at the end of the feed or after `FACEBOOK_MAX_PAGES`. Posts are handed over oldest first, keyed `facebook / <group> / <post_id>`, linked to the permalink; repeats are dropped by the ledger.
- Photos are the `viewer_image` of the attachments, downloaded from the CDN by URL. There is no author phone. The author (`FacebookDetails`) is the first actor of the story (under `comet_sections.context_layout`, then `comet_sections.content`, then the story's own `actors`) that has both a name and an `https` link on `facebook.com` (`profile_url`, else `url`, without the query; `profile.php` keeps only `?id=`): a post without a number in its text gets the profile name as its nickname, and is dropped as `no_phone` when there is no such actor. These JSON paths are not checked against a live answer yet.

A new source (a website, say) is one `@Component` implementing `PostSource` with its own name, in its own subpackage of `source/`, plus its `SourceDetails` if it needs any. It turns what it reads into `IncomingPost`s and hands them to the sink given to `start`; the queue, the ledger, the duplicate check and everything after are shared. `authorPhone` and `authorProfile` return empty when the source has no such notion.

## Publishers

`INGEST_PUBLISHERS` lists where a notice goes, in any order: `site`, `channel`. Empty is a dry run. There is no review chat: to try the channel out, point `TELEGRAM_PUBLISH_CHAT` at a private chat.

- `site` (`SitePublisher`) is special: it runs first, puts the photo into MinIO, creates the notice through the API entry and owns the ledger statuses `PUBLISHING`, `PUBLISHED`, `NEEDS_CHECK`, `FAILED`. Without `site` in the list a notice is recorded as `PREVIEWED` and goes nowhere else.
- Every other publisher implements `Publisher` and gets a `Publication`: the post, the notice, the model answer and the `SiteItem` (id and page URL) when `site` created one. If `site` is in the list and fails, nobody else is called.
- A publisher whose `needsSiteItem()` is true (`channel`) links to the notice page instead of carrying contacts; the start fails if it is listed without `site`.
- Publishers are independent: a failing one is logged (`WARN`) and counted in `derechi.ingest.published{publisher,outcome}`, the others still run, and the ledger status stays what `site` set. Skipped posts go to no publisher.

`channel` keeps the id of its post in `ingest_post.message_id` of the post's own row (`PostLedger.posted`). A notice from the site gets a row of its own once `ChannelMirror` has posted it (`PostLedger.postedFromSite`: `source` `site` or `admin`, `channel` `internal`, `external_id` `lost/7`, status `PUBLISHED`, the message id), and a notice that already has a row in `ingest_post` is not posted again. Either post can be deleted when the notice is archived or deleted.

Each publisher turns the `Notice` into its own format (`SitePublisher` into Client-API's `NewItem`, `ChannelPost.of` into a channel post), so the pipeline knows none of them.

A new publisher (a Viber group, say) is one `@Component` implementing `Publisher` with its own name, in its own subpackage of `publish/`; adding the name to `INGEST_PUBLISHERS` turns it on. An unknown name fails the start.

## Build and image

- TDLight is not on Maven Central: `Ingest/pom.xml` declares `https://mvn.mchv.eu/repository/mchv/`. Versions are pinned in the root `<dependencyManagement>` without importing TDLight's BOM, because an imported BOM would be resolved for every module.
- The TDLib native comes from an OS-activated Maven profile: the jar carries the native of the machine that builds it. The image is built on the machine it runs on (arm64 on OCI), so that is the right one.
- The root `Dockerfile` has a final stage of its own, `ingest` (compose `build.target`): it adds the Tesseract packages, sets `TESSDATA_PREFIX`, creates `/app/tdlib` for the session volume, and its entrypoint forwards arguments for `--login`. See [docker-image](../../deployment/docker-image.md).
- `JAVA_OPTS` needs `--enable-native-access=ALL-UNNAMED`: TDLight loads its native with `System.loadLibrary`, which JDK 26 warns about and later releases will block.
- The TDLib device model stays `DeRechi Telegram-Ingest`, the module's old name, so the existing session does not show up as a new device.

## Configuration

`derechi.telegram.*`, `derechi.bot.*`, `derechi.ingest.*`, `derechi.openrouter.*`, `derechi.places.*`, `derechi.api.*`, `derechi.storage.*`, `derechi.redaction.*`, `derechi.events.*`, one record each: `IngestProperties` in `config/`, the others in `config/telegram/`, `config/bot/`, `config/openrouter/`, `config/places/`, `config/client/`, `config/storage/`, `config/redaction/`, `config/events/` (with `RabbitConfig`), each with the `@Configuration` that builds its client where there is one. Every key has an environment variable; the list is in [environment variables](../../deployment/environment-variables.md). Missing keys do not fail the start: the stage reports itself unavailable, the queue retries the post 3 times 10 minutes apart, then it waits for the next catch-up. An unknown name in `INGEST_PUBLISHERS`, or `channel` without `site`, does fail it.

## Tests

Unit tests per stage (`*Test`), HTTP clients against `MockRestServiceServer`, `PostLedgerTests` and `IngestApplicationTests` on the Testcontainers database with `ddl-auto=validate`. Nothing talks to Telegram, OpenRouter or Google; `TesseractOcrEngineIT` runs only when `TESSDATA_PREFIX` is set. `support/Posts` and `support/Outputs` build the shared test posts and notices, `support/Images` the noise images and the variance check of the redaction tests.
