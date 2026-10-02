# Admin panel

`Admin-API` is a classic server-rendered application: Spring MVC, Thymeleaf, Bulma for styling and htmx for partial updates. There is no REST layer and no JSON API; everything under `/admin/**` returns HTML. Entry point in development: `http://localhost:8083/admin`.

## Signing in

Login is OIDC against Keycloak (client `derechi-admin`, Authorization Code, public client). Every request except `/actuator/**`, `/css/**`, `/js/**` and `/error` requires an authenticated session. Logout goes through Keycloak's end-session endpoint and lands back on `/admin`.

Dev users shipped with the realm:

| User | Password | Realm roles |
|---|---|---|
| `admin@derechi.local` | `admin` | `ADMIN_SUPER`, `USER` |
| `moderator@derechi.local` | `moderator` | `ADMIN_EDITOR`, `USER` |
| `viewer@derechi.local` | `viewer` | `ADMIN_VIEWER`, `USER` |
| `user@derechi.local` | `user` | `USER` (can log in, sees nothing) |

What each role can do is in [Permissions](permissions.md).

## Pages

| Page | Path | Controller | Scope |
|---|---|---|---|
| Matches | `/admin/matches` | `MatchController` | `MATCH` |
| Lost items | `/admin/lost-items` | `LostItemController` | `LOST_ITEM` |
| Found items | `/admin/found-items` | `FoundItemController` | `FOUND_ITEM` |
| Lost archive | `/admin/lost-items-history` | `LostItemHistoryController` | `LOST_ITEM_HISTORY` |
| Found archive | `/admin/found-items-history` | `FoundItemHistoryController` | `FOUND_ITEM_HISTORY` |
| Uploads (XHR only) | `/admin/uploads` | `UploadController` | any item CREATE or EDIT |

`/admin` itself redirects to `/admin/matches`. The four item pages share `ItemController<T>` (list, new, create, edit, update, archive, delete) and `AdminItemService<T>`; only the entity type, the scope and the templates differ.

### Lists

`items/list.html` renders a filterable, sortable, paginated table. Filters (`fragments/filters.html`): text query, category, date range. Sorting (`fragments/sorting.html`) is restricted to `id`, `title`, `date`. Page size is `derechi.admin.page-size` (20). Row actions open `<dialog>` elements from `fragments/dialogs.html` for view, archive and delete confirmations. See [Search and filtering](search-and-filtering.md).

### Form

`items/form.html` backs both create and edit through `ItemForm` (Lombok getters and setters, Bean Validation). Fields: title, description, date (not in the future), compensation and currency, category, place (Google Places autocomplete filling `placeId`, `placeName`, `lat`, `lon`, `countryCode`), phone (E.164), email, social networks (checkboxes over `ContactInfo.SocialMediaEnum`), and the photo dropzone. `ItemFormValidator` adds the country and currency checks that annotations cannot express. Validation messages come from the message bundles, so they are localized.

The photo is uploaded as soon as it is dropped (`POST /admin/uploads`) and the returned key is stored in the hidden `image` field; see [Image uploads](image-uploads.md).

### Archive versus delete

Delete is synchronous and final: it removes the row from `lost_item` or `found_item` together with its `similar_item` pairs and its claims with their contact infos. Archive only publishes an `ArchiveRequestedEvent`; `Worker` then copies the notice into `lost_item_history` or `found_item_history` with `archived_at = now()`, re-points its claims at the copy, drops the matches and deletes the row, a moment after the click. The flash says the notice is being archived and the list catches up on the next load. The archive pages list the history tables with the same filters but no form, including the responses each notice had (see [Claims](claims.md)).

## Permissions in templates

`GlobalModelAdvice` exposes `perms` (an `AdminPermissions` view of the current authorities) to every template, so buttons and nav entries are guarded with

```html
<a th:if="${perms.can('LOST_ITEM', 'CREATE')}" ...>
```

The controller method carries the matching `@RequirePermission`, so a hand-crafted request without the button still gets 403.

## Language

The header has a language menu listing `LocaleConfig.SUPPORTED`; picking one adds `?lang=xx`, and `LocaleChangeInterceptor` stores it in the `DERECHI_LOCALE` cookie for a year. All copy, including validation and error messages, comes from `messages*.properties`. See [Localization](localization.md).

## Caching

- Controllers annotated `@CachedPage` (default 60 s) let the browser keep GET responses in its private cache; `CacheControlInterceptor` sets `no-store` on everything else and busts the cached list after a redirect.
- The category list is cached in Caffeine (`categories`, 1 hour).
- Static files are served with content hashes in the URL and a 14-day public max-age, so CSS and JS changes do not require users to clear anything.
- Thymeleaf template cache is on, so template edits need a restart in development.

## Error pages

`templates/error/403.html`, `404.html`, `408.html`, `500.html`. `GlobalExceptionHandler` maps `NotFoundException` to 404 with a localized message built from the entity key (for example `entity.LOST_ITEM`) and the id, `AccessDeniedException` to 403, and treats query timeouts (30 s transaction and query timeout) as 408.

## Partial updates

htmx is loaded from a CDN in `fragments/layout :: head`. The Matches page uses it for loading more candidates and for the Notify button; the loading spinner, the error text and date re-formatting are wired once in `static/js/admin.js`. The rules that keep this from breaking are in [Admin UI conventions](../conventions/admin-ui.md).

## Where to look

- `Admin-API/src/main/java/org/shpytchuk/adminapi/controller/`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/service/AdminItemService.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/config/SecurityConfig.java`, `LocaleConfig.java`, `cache/`
- `Admin-API/src/main/resources/templates/`
- Tests: `LostItemControllerTests`, `LostItemHistoryControllerTests`, `ItemControllerPermissionsTests`, `ItemFormPlaceSearchTests`, `MatchControllerTests`, `UploadControllerTests`, `GlobalExceptionHandlerTests`, `AdminItemServiceTests`, `MatchServiceTests`, `PagesTest`, `MessagesTest`
