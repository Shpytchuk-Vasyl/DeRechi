# Ingest

Imports lost-and-found notices from outside sources and publishes them. Today the only source is Telegram: a userbot session reads the chats of one Telegram folder, a model through OpenRouter extracts the notice, and the result goes to the publishers in `INGEST_PUBLISHERS`: the site (Client-API's public GraphQL), our Telegram channel, the review chat. What it does step by step is in [the feature page](../../features/telegram-ingest.md).

| | |
|---|---|
| Port | 8086 (serves nothing public); actuator on the management port 9086 |
| Consumes | Telegram updates (TDLib through TDLight), no queue |
| Produces | notices through the API entry, Caddy for now (`createLostItem` / `createFoundItem`), photos in MinIO, posts in our Telegram channel and the review chat (Bot API) |
| Needs | PostgreSQL (`ingest_post`), the API entry (Caddy) with Client-API behind it, MinIO; OpenRouter, Google Places, Telegram |
| Stack | TDLight Java (TDLib 1.8.66), libphonenumber, Tess4J + system Tesseract, ONNX Runtime + YuNet, Spring for GraphQL client, Spring Cloud AWS S3, Lombok |
| Instances | exactly one: the TDLib session cannot be shared |

## Packages

One package per stage, each behind an interface the pipeline sees; the implementation is the only class that knows the outside service. The clients of outside services sit under `api/`: the model (`api/ai/`), Google Places (`api/geo/`), Client-API (`api/derechi/`) and MinIO (`api/storage/`).

| Package | Interface | Implementation |
|---|---|---|
| `source/` | `PostSource` | `PostSources` (all sources by name), `IncomingPost`, `PostKey`, `SourceSettings`, `PhotoRef`, `SourceDetails` |
| `telegram/` | `TelegramSource` | `TelegramPostSource` (the `PostSource` named `telegram`), `TdlightTelegramSource`, `MessageMapper`, `PostBuffer` and `PostGroup` (an album or a text with its photos sent separately become one post), `AuthorPhoneResolver`, `TelegramDetails`, `TelegramLogin` (`--login`) |
| `phone/` | | `PhoneExtractor`, `Masker`, `PhoneHasher` |
| `image/` | `ImageRedactor`, `OcrEngine`, `FaceDetector` | `DefaultImageRedactor`, `TesseractOcrEngine`, `YuNetFaceDetector`, `DocumentDetector`, `Blur` |
| `api/ai/llm/` | `Extractor` | `OpenRouterExtractor`, `PromptBuilder`, `ExtractionPolicy`, `Extraction` (the model's answer), `DailyCallCounter` |
| `api/ai/model/` | | `ItemKind`, `Money`, `SocialMedia`, `Outcome`: the values shared by the model's answer, the notice and the metrics |
| `api/geo/` | `PlaceResolver` | `GoogleLegacyPlaceResolver` (`GOOGLE_PLACES_API=LEGACY`, default) or `GooglePlaceResolver` (`NEW`), both on `TextSearchPlaceResolver` |
| `api/derechi/` | `DeRechiApi` | `GraphQlDeRechiApi`, `NewItem` |
| `api/storage/` | `ImageUploader` | `S3ImageUploader` |
| `publish/` | `Publisher` | `Publishers` (runs the list of `INGEST_PUBLISHERS`), `SitePublisher` (`site`), `Publication`, `SiteItem`, `SkippedPost` |
| `channel/` | | `ChannelPoster` (`channel`), `ReviewChat` (`review`), `BotApiClient`, `CaptionBuilder` |
| `pipeline/` | | `IngestQueue`, `IngestPipeline`, `PostLedger`, `IngestMetrics`, `Notice`, `Notices` |

## Sources

A post enters as an `IncomingPost`: a `PostKey` (source name, channel, id within the channel; the unique key of `ingest_post`), the source title and link, the text, `PhotoRef`s, the `SourceSettings` of its channel (region, default city, locale) and `SourceDetails` only its source understands (`TelegramDetails`: chat and message ids, the author, mentions). The pipeline asks the post's source, found by `PostSources` by name, to download a photo and to look for the author's phone when the text has none.

A new source (a website, say) is one `@Component` implementing `PostSource` with its own name, plus its `SourceDetails` if it needs any. It turns what it reads into `IncomingPost`s and hands them to the sink given to `start`; the queue, the ledger, the duplicate check and everything after are shared. `authorPhone` returns empty when the source has no such notion.

## Publishers

`INGEST_PUBLISHERS` lists where a notice goes, in any order: `site`, `channel`, `review`.

- `site` (`SitePublisher`) is special: it runs first, puts the photo into MinIO, creates the notice through the API entry and owns the ledger statuses `PUBLISHING`, `PUBLISHED`, `NEEDS_CHECK`, `FAILED`. Without `site` in the list a notice is recorded as `PREVIEWED`.
- Every other publisher implements `Publisher` and gets a `Publication`: the post, the notice, the model answer, the redacted photos and the `SiteItem` (id and page URL) when `site` created one. If `site` is in the list and fails, nobody else is called.
- A publisher whose `needsSiteItem()` is true (`channel`) links to the notice page instead of carrying contacts; the start fails if it is listed without `site`.
- Publishers are independent: a failing one is logged (`WARN`) and counted in `derechi.ingest.published{publisher,outcome}`, the others still run, and the ledger status stays what `site` set. Skipped posts go to `Publisher.skipped`, which only `review` uses.

A new publisher (a Viber group, say) is one `@Component` implementing `Publisher` with its own name; adding the name to `INGEST_PUBLISHERS` turns it on. An unknown name fails the start.

## Build and image

- TDLight is not on Maven Central: `Ingest/pom.xml` declares `https://mvn.mchv.eu/repository/mchv/`. Versions are pinned in the root `<dependencyManagement>` without importing TDLight's BOM, because an imported BOM would be resolved for every module.
- The TDLib native comes from an OS-activated Maven profile: the jar carries the native of the machine that builds it. The image is built on the machine it runs on (arm64 on OCI), so that is the right one.
- The root `Dockerfile` has a final stage of its own, `ingest` (compose `build.target`): it adds the Tesseract packages, sets `TESSDATA_PREFIX`, creates `/app/tdlib` for the session volume, and its entrypoint forwards arguments for `--login`. See [docker-image](../../deployment/docker-image.md).
- `JAVA_OPTS` needs `--enable-native-access=ALL-UNNAMED`: TDLight loads its native with `System.loadLibrary`, which JDK 26 warns about and later releases will block.
- The TDLib device model stays `DeRechi Telegram-Ingest`, the module's old name, so the existing session does not show up as a new device.

## Configuration

`derechi.telegram.*`, `derechi.bot.*`, `derechi.ingest.*`, `derechi.openrouter.*`, `derechi.places.*`, `derechi.api.*`, `derechi.storage.*`, `derechi.redaction.*`, one record each in `config/`. Every key has an environment variable; the list is in [environment variables](../../deployment/environment-variables.md). Missing keys do not fail the start: the stage reports itself unavailable and the post waits for the next catch-up. An unknown name in `INGEST_PUBLISHERS`, or `channel` without `site`, does fail it.

## Tests

Unit tests per stage (`*Test`), HTTP clients against `MockRestServiceServer`, `PostLedgerTests` and `IngestApplicationTests` on the Testcontainers database with `ddl-auto=validate`. Nothing talks to Telegram, OpenRouter or Google; `TesseractOcrEngineIT` runs only when `TESSDATA_PREFIX` is set. `support/Posts` and `support/Outputs` build the shared test posts and notices.
