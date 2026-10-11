# Telegram-Ingest

Imports lost-and-found notices from Telegram channels and groups: a userbot session reads the chats of one Telegram folder, a model through OpenRouter extracts the notice, and the result is created on the site through Client-API's public GraphQL and reposted to our channel. What it does step by step is in [the feature page](../../features/telegram-ingest.md).

| | |
|---|---|
| Port | 8086 (serves nothing public); actuator on the management port 9086 |
| Consumes | Telegram updates (TDLib through TDLight), no queue |
| Produces | notices through the Gateway (`createLostItem` / `createFoundItem`), photos in MinIO, posts in our Telegram channel (Bot API) |
| Needs | PostgreSQL (`telegram_post`), the Gateway with Client-API behind it, MinIO; OpenRouter, Google Places API (New), Telegram |
| Stack | TDLight Java (TDLib 1.8.66), libphonenumber, Tess4J + system Tesseract, ONNX Runtime + YuNet, Spring for GraphQL client, Spring Cloud AWS S3, Lombok |
| Instances | exactly one: the TDLib session cannot be shared |

## Packages

One package per stage, each behind an interface the pipeline sees; the implementation is the only class that knows the outside service.

| Package | Interface | Implementation |
|---|---|---|
| `telegram/` | `TelegramSource` | `TdlightTelegramSource`, `MessageMapper`, `AlbumBuffer`, `TelegramLogin` (`--login`) |
| `phone/` | | `PhoneExtractor`, `AuthorPhoneResolver`, `Masker`, `PhoneHasher` |
| `image/` | `ImageRedactor`, `OcrEngine`, `FaceDetector` | `DefaultImageRedactor`, `TesseractOcrEngine`, `YuNetFaceDetector`, `DocumentDetector`, `Blur` |
| `llm/` | `Extractor` | `OpenRouterExtractor`, `PromptBuilder`, `ExtractionPolicy` |
| `geo/` | `PlaceResolver` | `GooglePlaceResolver` |
| `api/` | `DeRechiApi` | `GraphQlDeRechiApi` |
| `storage/` | `ImageUploader` | `S3ImageUploader` |
| `channel/` | | `ChannelPoster` (public channel), `ReviewChat` (DRY_RUN previews), `BotApiClient`, `CaptionBuilder` |
| `pipeline/` | `NoticeOutput` | `IngestQueue`, `IngestPipeline`, `PreviewOutput` (DRY_RUN) and `PublishingOutput` (AUTO), one bean per `INGEST_MODE`; `PostLedger`, `IngestMetrics`, `Notice`, `Notices` |

## Build and image

- TDLight is not on Maven Central: `Telegram-Ingest/pom.xml` declares `https://mvn.mchv.eu/repository/mchv/`. Versions are pinned in the root `<dependencyManagement>` without importing TDLight's BOM, because an imported BOM would be resolved for every module.
- The TDLib native comes from an OS-activated Maven profile: the jar carries the native of the machine that builds it. The image is built on the machine it runs on (arm64 on OCI), so that is the right one.
- The root `Dockerfile` has a final stage of its own, `telegram-ingest` (compose `build.target`): it adds the Tesseract packages, sets `TESSDATA_PREFIX`, creates `/app/tdlib` for the session volume, and its entrypoint forwards arguments for `--login`. See [docker-image](../../deployment/docker-image.md).
- `JAVA_OPTS` needs `--enable-native-access=ALL-UNNAMED`: TDLight loads its native with `System.loadLibrary`, which JDK 26 warns about and later releases will block.

## Configuration

`derechi.telegram.*`, `derechi.bot.*`, `derechi.ingest.*`, `derechi.openrouter.*`, `derechi.places.*`, `derechi.api.*`, `derechi.storage.*`, `derechi.redaction.*`, one record each in `config/`. Every key has an environment variable; the list is in [environment variables](../../deployment/environment-variables.md). Missing keys do not fail the start: the stage reports itself unavailable and the post waits for the next catch-up.

## Tests

Unit tests per stage (`*Test`), HTTP clients against `MockRestServiceServer`, `PostLedgerTests` and `TelegramIngestApplicationTests` on the Testcontainers database with `ddl-auto=validate`. Nothing talks to Telegram, OpenRouter or Google; `TesseractOcrEngineIT` runs only when `TESSDATA_PREFIX` is set.
