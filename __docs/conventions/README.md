# Conventions

The rules we follow when writing code in this repository, each with the reason behind it. Most of them exist because something broke once, so read the "why" before deciding a rule does not apply to your case.

- [Java code style](java-code-style.md) - records for data, Lombok only on entities and forms, transactions, naming and package layout.
- [Logging](logging.md) - one line per event with ids, levels, the root cause instead of a stack trace, no personal data, admin audit.
- [Maven and modules](maven-and-modules.md) - how the reactor is laid out, where versions live, and what an Initializr-generated module must lose before it is committed.
- [Configuration](configuration.md) - `application.yaml` is the dev profile, containers override through environment variables, typed properties under `derechi.*`.
- [Database migrations](database-migrations.md) - formatted SQL changesets in `DB-Postgres`, numbering, what to fix by hand in generated diffs.
- [Internationalization](i18n.md) - five message bundles, no literal text in templates, plurals and formats.
- [Admin UI](admin-ui.md) - Thymeleaf fragments, Bulma, htmx rules, caching and permissions in templates.
- [Testing](testing.md) - what kinds of tests exist, Testcontainers, how to run them and what a new feature must ship with.
- [Git](git.md) - branch names, commit messages, what never goes into the repository.

For the development flow itself (branch, PR, review, release) see the [developer guide](../developer-guide/README.md) and [processes](../processes/README.md).
