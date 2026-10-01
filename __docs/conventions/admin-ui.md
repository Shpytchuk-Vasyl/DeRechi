# Admin UI

`Admin-API` is a server-rendered Spring MVC application: Thymeleaf templates on Bulma, htmx for partial updates, a few small scripts under `static/js`. There is no REST layer and no SPA. What the panel does is described in [admin-panel](../features/admin-panel.md); this page is how we build screens in it.

## Templates and fragments

```
templates/
  fragments/layout.html      head(pageTitle), navbar, flash, pager(pages, basePath, query)
  fragments/filters.html     bar(filter, categories, basePath, sort), hidden(filter)
  fragments/sorting.html     header(column, label, sort, basePath, query)
  fragments/dialogs.html     thumb(prefix, item, size), trigger(prefix, item), dialogs(prefix, item, editBase, scope)
  fragments/candidates.html  cell(row, page, filter), row(lost, candidate)
  items/list.html, items/form.html
  matches.html
  error/403.html, 404.html, 408.html, 500.html
```

Rules:

- Every page starts with `~{fragments/layout :: head(...)}` and `~{fragments/layout :: navbar}`. Bulma, Font Awesome, flag-icons, FilePond and htmx are loaded from CDNs in `head`; do not add a second copy of any of them to a page.
- Repeated markup is a fragment with explicit parameters, not a copy. A `<dialog>` for an item is rendered through `dialogs :: dialogs(prefix, item, editBase, scope)` and its `id` is `prefix-id`, so the same item can be shown in different contexts without duplicate ids.
- `dialogs :: dialogs(...)` renders the "Responses" block only when the model carries a `claims` map that has the item's id; the live item lists put every id of the page in it, the archives and the matches page pass nothing and get no block.
- A fragment that htmx swaps in must be **self-contained**: anything that belongs to the swapped markup (dialogs, hidden forms) lives inside the fragment. That is why the found-item dialogs for match candidates sit in `candidates :: cell` and not at the bottom of `matches.html`; after a swap the document would otherwise contain two dialogs with the same id.
- Visible text is always `#{key}`; see [i18n](i18n.md).

## Bulma

Use Bulma classes and the handful of custom ones in `static/css/admin.css` (`thumb`, `thumb-img`, `thumb-fallback`, `app-dialog`, `photo-dialog`, `local-datetime`). Do not add inline styles or a second CSS framework. Row actions are a Bulma `dropdown`; the "Notify" split button follows the same markup.

## htmx

htmx is used for two things today: loading the full candidate list for a lost item and sending a notification for a single candidate. The conventions carry over to anything new.

**Global handlers live in `admin.js` on `document`, not per element.** They cover every htmx request on the page:

| Event | Effect |
|---|---|
| `htmx:beforeRequest` / `htmx:afterRequest` | add / remove Bulma `is-loading` on the element that triggered the request |
| `htmx:responseError` / `htmx:sendError` | mark the element `is-danger is-light` and show `data-error` as its text and title |
| `htmx:load` | run `formatLocalTimes` on the new markup so `<time class="local-datetime">` is rendered the same way as on the initial page |

A new htmx control therefore needs only the `hx-*` attributes plus `th:data-error="#{some.key}"`; the error text must come from the template because it is localized.

**Swap the whole element with `outerHTML`.** The candidates cell is replaced as a unit:

```html
<button th:hx-get="@{/admin/matches/{id}/candidates(id=${row.lost.id})}"
        th:hx-target="'#candidates-' + ${row.lost.id}"
        hx-swap="outerHTML">
```

and the notify button replaces only its own `<tr id="candidate-{lostId}-{foundId}">`. Never append rows to a table with out-of-band elements in the response: the browser's HTML parser moves anything that is not a `<tr>` out of the table before htmx sees it, and dialogs end up duplicated.

**Forms, not custom JS.** The "Notify" split button is several `<button name="channel" value="...">` submits of one `<form>` with `hx-post`. htmx sends the form's hidden fields (including `_csrf`) and the clicked button's name/value by itself. No `hx-vals`, no `fetch`, no inline handlers.

**A partial endpoint returns the fragment, not a redirect.** `POST /admin/matches/notify` responds with `candidates :: row` so the state ("Notified ...", button turns `is-light`) is visible in place. Full-page actions (create, update, archive, delete) keep the classic redirect with a flash message.

## Caching

`CacheControlInterceptor` sets `Cache-Control: no-store` on every response. A controller annotated `@CachedPage(seconds = ...)` (default 60) gets `max-age` for its **GET** responses only, in the browser's private cache; the response to a POST that follows clears the cached list through the redirect. Use `@CachedPage` on list and read-only controllers (`ItemController`, `MatchController`), never on anything that returns user-specific or freshly mutated data without a redirect.

Static files are served with a content-hash in the URL (`spring.web.resources.chain.strategy.content`) and `max-age: 14d`, so edit a script freely; the URL changes with the content. Reference assets with `@{/js/admin.js}` so the hashed URL is generated.

The category reference list is cached in Caffeine (`categories`, one hour). A new reference lookup that rarely changes can use the same cache name pattern.

## Permissions

Every handler method carries `@RequirePermission(scope = Scope.X, action = Action.Y)`; `UploadController` uses `@PreAuthorize("@perm.canManageItems(authentication)")` because it serves several scopes. In templates, hide what the user cannot do with `${perms.can('LOST_ITEM', 'EDIT')}` (or `perms.can(scope, 'EDIT')` when the scope is a model attribute). Hiding a button is a courtesy; the annotation is the actual check, and a missing annotation is a security bug. See [permissions](../features/permissions.md).

## Lists, sorting, paging

- Controllers accept a `Pageable`; the page size default comes from `derechi.admin.page-size`.
- Sorting from the query string passes through `Pages.safe(...)`, which keeps only `id`, `title` and `date` and falls back to newest first. Add a column to `Pages.SORTABLE` only if it is indexed or the table is small.
- The template receives `SortView` (current property, direction, helpers for the header links) and `Pager.of(page)` for the page numbers; render them with `sorting :: header` and `layout :: pager`.
- Filters are an `ItemFilter` record bound from request parameters; `filters :: hidden(filter)` re-emits them as hidden inputs so sorting and paging links keep the filter.

## Forms

`ItemForm` is a Lombok class with Bean Validation annotations. Rules that need configuration (country and currency must be among the supported ones) live in `ItemFormValidator`, registered on the binder with `@InitBinder("form")` in `ItemController`, so its errors land in the same `BindingResult` and render under the field. Do not validate in the service and translate the exception back into a field error; the service check is only a guard against calls that bypass the controller.

Place selection uses the Google Places widget in `place-autocomplete.js`. The input carries `data-region` (language/region bias) and `data-countries` (restricts suggestions to the supported countries, maximum five per Google's limit), both filled from the model. The country select stays editable for manual entry.

Image upload is FilePond (`image-upload.js`), posting to `/admin/uploads` and reverting with a DELETE of the key; the input's `data-max-size`, `data-too-large` and `data-unsupported` come from `StorageProperties` and the bundles.

## Checklist for a new screen

1. Controller: `@RequirePermission` on every method, `@CachedPage` if it is a read-only list, `Pages.safe` on the `Pageable`.
2. Service returns records (`*View`), never entities, and runs in a read-only transaction.
3. Template: `layout :: head` + `navbar`, text only from `#{...}`, keys added to five bundles.
4. Any htmx control: `hx-target` an element with a stable id, `hx-swap="outerHTML"`, `th:data-error`.
5. A `WebMvcTest` with `oidcLogin().authorities(...)` covering both the allowed and the forbidden case.
