# Features

What the system does today, feature by feature. Each page covers the user-facing behaviour, the modules involved, the key classes and config, and the known gaps. When a feature has a documented way to grow, the page links to the matching checklist under [extending](../extending/README.md).

- [Notices](notices.md): lost and found notices, the GraphQL mutations and queries behind them, contact masking.
- [Search and filtering](search-and-filtering.md): text search, category and date filters, geo radius, sorting, place lookup.
- [Automatic matching](automatic-matching.md): how a new notice is matched against the opposite kind in the background.
- [Match notifications](match-notifications.md): the admin "Matches" page and the path from the Notify button to an email.
- [Claims](claims.md): "it's mine" / "I found it" on a notice, contacts passed to the author, reminders and automatic archiving.
- [Admin panel](admin-panel.md): pages, forms, archive versus delete, caching and error pages.
- [Permissions](permissions.md): the Scope × Action model, Keycloak roles and how a request is checked.
- [Localization](localization.md): five UI languages, the locale cookie, plurals and formats.
- [Countries and currencies](countries-and-currencies.md): supported countries, reward currency rules.
- [Image uploads](image-uploads.md): photo storage in MinIO from the admin panel and from the web client.
- [Places](places.md): the Place entity, Google Places autocomplete and geo queries.
