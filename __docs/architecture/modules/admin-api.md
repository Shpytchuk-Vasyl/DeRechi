# Admin-API

The administration panel: a server-rendered Spring MVC application with Thymeleaf
templates, Bulma for styling and htmx for partial page updates. Administrators log in
through Keycloak and work with lost and found notices, their archives, and the candidate
matches produced by `Worker`. There is no REST API and no OpenAPI here; every
endpoint returns HTML (or a fragment of it).

What the panel does from a user's point of view is in
[../../features/admin-panel.md](../../features/admin-panel.md). This page is about how it is
built.

| | |
|---|---|
| Port | 8083 |
| Entry point | `http://localhost:8083/admin` (`/` redirects there) |
| Needs | PostgreSQL, RabbitMQ (to publish notifications), MinIO (uploads), Keycloak (login), Discovery |
| Stack | Spring MVC, Thymeleaf, Spring Security OAuth2 client, Spring Data JPA + Hibernate Spatial, Spring AMQP, Spring Cloud AWS S3, Caffeine, libphonenumber, Lombok |

## Controllers

| Controller | Base path | Permissions (`Scope`) |
|---|---|---|
| `AdminHomeController` | `/admin` | authenticated |
| `LostItemController` | `/admin/lost-items` | `LOST_ITEM` |
| `FoundItemController` | `/admin/found-items` | `FOUND_ITEM` |
| `LostItemHistoryController` | `/admin/lost-items-history` | `LOST_ITEM_HISTORY` |
| `FoundItemHistoryController` | `/admin/found-items-history` | `FOUND_ITEM_HISTORY` |
| `MatchController` | `/admin/matches` | `MATCH` (`VIEW` for the list and `/{lostItemId}/candidates`, `NOTIFY` for `POST /notify`) |
| `UploadController` | `/admin/uploads` | any item `CREATE` or `EDIT` |

The four item controllers extend `ItemController<T>`, which carries the shared list, form,
archive and delete handling; the subclasses add the mappings and the `@RequirePermission`
annotations, because the annotation needs a constant scope. Actions follow one pattern:
`GET` list, `GET /new`, `POST` create, `GET /{id}/edit`, `POST /{id}` update,
`POST /{id}/archive`, `POST /{id}/delete`. Forms are `POST` only; there is no `PUT` or
`DELETE` from HTML.

Templates: `items/list.html` and `items/form.html` are shared by all four item kinds,
`matches.html` is the match page, `fragments/` holds the layout, filters, sorting, dialogs
and the `candidates` fragments that htmx swaps in, `error/` has 403/404/408/500 pages.

## Security

`SecurityConfig`: everything but `/actuator/**`, static assets and `/error` requires login;
`oauth2Login()` against the `keycloak` registration; OIDC-initiated logout back to
`/admin`; `@EnableMethodSecurity`. The Keycloak side is described in
[../authentication.md](../authentication.md).

Permissions are two-dimensional, `Scope` × `Action`
(`Admin-API/src/main/java/org/shpytchuk/adminapi/security/`):

- `Scope` lists which actions it supports (`MATCH` has only `VIEW` and `NOTIFY`, the
  history scopes have no `ARCHIVE`). `Permissions.authority(scope, action)` produces the
  `SCOPE:ACTION` string and refuses an unsupported pair.
- `@RequirePermission(scope = ..., action = ...)` is a meta-annotation over
  `@PreAuthorize("@perm.can(authentication, '{scope}', '{action}')")`. The `{scope}`
  placeholders work because `SecurityConfig` registers
  `AnnotationTemplateExpressionDefaults`.
- `perm` is the `PermissionChecker` bean; `AdminPermissions` is the per-request view of the
  authorities and is also put into every model as `perms`, so templates can write
  `${perms.can('LOST_ITEM', 'EDIT')}` to hide a button.

Adding a scope or action is covered in
[../../extending/add-an-admin-permission.md](../../extending/add-an-admin-permission.md).

## Rendering helpers

`GlobalModelAdvice` adds the same attributes to every model:

| Attribute | Bean | Use in templates |
|---|---|---|
| `fmt` | `Formats` | `date`, `money(amount, currency)`, `country(code)`, `phone` (libphonenumber), all locale-aware |
| `plural` | `Plurals` | `records(count)` and `format(key, count)` with `one/few/many/other` keys, because Slavic plural rules do not fit `MessageFormat` |
| `social` | `SocialMediaIcons` | Font Awesome class per `SocialMediaEnum` |
| `uploads` | `ImageStorage` | `urlOf(key)` for thumbnails, `maxBytes` for the drop zone |
| `languages`, `currentLanguage` | `LocaleConfig` | the language switcher |
| `perms`, `currentUser` | security | permission checks, header |

Localization (`LocaleConfig`, the five `messages*.properties` bundles, the `DERECHI_LOCALE`
cookie and `?lang=`) is described in
[../../features/localization.md](../../features/localization.md). `MessagesTest` fails the
build if a key or a message argument is missing from any bundle.

## Caching

Three separate mechanisms, chosen by how stale the data may be:

- **Browser cache for pages.** `@CachedPage(seconds = ...)` on a controller class makes
  `CacheControlInterceptor` answer successful `GET`s with `max-age` and `private`;
  everything else gets `no-store`. The default is 60 seconds.
- **Caffeine for the category list.** Cache `categories`, `expireAfterWrite=1h`,
  configured in `application.yaml` and used by the category repository lookup. Categories
  change only by migration.
- **Content-hashed static assets.** `spring.web.resources.chain.strategy.content` renames
  `admin.css` and the scripts with a hash of their content, and the response carries a
  14-day public `max-age`. Templates reference them through Thymeleaf's `@{...}` so the
  hashed name is resolved at render time.

Thymeleaf's own template cache is on (`spring.thymeleaf.cache: true`), so template edits
need a restart or a DevTools reload.

## Lists, sorting and paging

`WebConfig` sets the fallback `Pageable` to page 0, `derechi.admin.page-size` (20) rows,
newest first, and caps the page size at 100. The `sort` request parameter is accepted but
`Pages.safe(...)` keeps only `id`, `title` and `date`; without that whitelist any entity
attribute name could end up in `ORDER BY`. `SortView` renders the current state back into
the column headers. Filters bind to `ItemFilter` and turn into `ThingSpecifications`.

## Matches and notifications

`MatchService` pages lost items with their candidates: for each page it loads at most
`PREVIEW_SIZE` (3) candidates per lost item and the total count in two queries, and
`rowWithAllCandidates` loads everything for one lost item when the user asks for more.

`MatchNotificationService.notifyOwner(lostId, foundId, actor, channel)` builds a localized
`NotificationRequestedEvent` (subject and body from the message bundle, in the admin's
current locale), keeps only the contacts the chosen `NotifyChannel` needs, publishes to
`derechi.notifications` with the key from `derechi.notifications.routing-key`, stamps
`notified_at` / `notified_by` on the `similar_item` row, and returns a `NotifiedMatch` for
re-rendering the row. The htmx choreography around it is in
[../../features/match-notifications.md](../../features/match-notifications.md).

## Claims

The lost and found lists and both archives show how many people responded to a notice and
the item dialog lists them (phone, email, when, status); the archives look claims up by the
history copy. `AdminItemService.deleteClaims` runs next to `deleteMatches` on delete, removing
the claims and their contact infos. Archive does not touch rows at all: it publishes
`ArchiveRequestedEvent` (`derechi.archive.exchange`, `item.<kind>.archive`) and
`Worker` archives the notice. See [../../features/claims.md](../../features/claims.md).

## Uploads and maps

`ImageStorage` + `UploadController` are described in [../file-storage.md](../file-storage.md).

The place field in the item form is a Google Places autocomplete
(`static/js/place-autocomplete.js`). `MapsProperties` (`derechi.maps.api-key`, `region`) is
exposed to the template through the `GoogleMaps` bean; `CountriesProperties` narrows the
autocomplete to supported countries and `ItemFormValidator` rejects an unsupported country
or currency as a field error. See
[../../features/countries-and-currencies.md](../../features/countries-and-currencies.md)
and [../../features/places.md](../../features/places.md).

## Config

| Key | Default | Env in Compose |
|---|---|---|
| `derechi.admin.client-id` | `derechi-admin` | |
| `derechi.admin.page-size` | 20 | |
| `derechi.maps.api-key`, `region` | from `GOOGLE_MAPS_API_KEY`, `UA` | |
| `derechi.countries.supported`, `fallback` | `[UA, PL, DE, FR]`, `UA` | |
| `derechi.storage.bucket`, `public-url`, `max-size` | `derechi-files`, `MINIO_PUBLIC_URL`, 5MB | `MINIO_PUBLIC_URL=http://localhost:8080/files` |
| `derechi.notifications.exchange`, `routing-key` | `derechi.notifications`, `notification.match.found` | |
| `spring.cloud.aws.s3.endpoint` | `MINIO_ENDPOINT`, `http://localhost:9000` | `MINIO_ENDPOINT=http://minio:9000` |
| `spring.security.oauth2.client.provider.keycloak.*` | `issuer-uri` (default profile) | `SPRING_PROFILES_ACTIVE=docker`, `KEYCLOAK_URI`, `KEYCLOAK_PUBLIC_URI` |

Plus the usual datasource, RabbitMQ and Eureka settings. Hikari `connection-timeout`,
the transaction default timeout and the JPA query timeout are all 30 seconds; response
compression is on for text and JSON.

## Tests

| Test | Kind |
|---|---|
| `LostItemControllerTests`, `LostItemHistoryControllerTests`, `MatchControllerTests`, `ItemFormPlaceSearchTests` | `@WebMvcTest` slices importing `SecurityConfig` and the view helpers, services and `ImageStorage` mocked (the real one would pull in the S3 client) |
| `service/matching/MatchNotificationServiceTest`, `service/lost/LostItemAdminServiceTest` | unit tests with mocked repositories and `RabbitTemplate`, next to the services they cover |
| `PermissionsTest`, `NotifyChannelTest`, `ItemFilterTest`, `FormatsTest`, `CountriesPropertiesTest` | plain unit tests |
| `MessagesTest` | bundle parity across the five languages |
| `ImageStorageIT` | `@SpringBootTest` against a live MinIO on `localhost:9000`, enabled only when its health endpoint answers |
| `AdminApiApplicationTests` | context load |

```bash
./mvnw -pl Admin-API test
./mvnw -pl Admin-API spring-boot:run      # then http://localhost:8083/admin
```
