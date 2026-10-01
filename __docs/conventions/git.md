# Git

What a branch is called, what a commit says, and what never enters the repository. The day-to-day flow (when to branch, how to open and merge a pull request) is in [branches-and-commits](../developer-guide/branches-and-commits.md) and [pull-requests](../developer-guide/pull-requests.md).

## Branches

`main` is the integration branch and the only long-lived one. Today it is also the only branch in the repository; the rules below describe the convention from here on, not the history.

- `main` is protected: no direct pushes, changes arrive through a pull request with at least one review and a green build.
- Work happens on short-lived branches named `type/short-topic`, lowercase, words separated by hyphens:

| Prefix | Use |
|---|---|
| `feature/` | new behaviour: `feature/signal-contact`, `feature/countries-and-currencies` |
| `fix/` | bug fix: `fix/notify-channel-sms-fallback` |
| `chore/` | build, dependencies, infrastructure: `chore/boot-4-1-2` |
| `..` | documentation only: `..` |
| `refactor/` | behaviour-preserving restructuring |

- If the work tracks an issue, put its number first: `feature/123-signal-contact`. The number alone is not enough; the topic must be readable in `git branch`.
- One branch, one concern. A refactoring you discovered on the way gets its own branch and its own pull request.
- Rebase on `main` before opening the pull request and whenever `main` moves under you. We prefer a linear history over merge commits inside a feature branch.

## Commits

The style follows the existing history, which is imperative, lowercase and short:

```
add support for place search
deliver match notifications only through the requested channel
drop the messenger fan-out until recipients can be addressed
```

Rules:

- Subject in the imperative mood ("add", "fix", "remove", "deliver"), lowercase first letter, no trailing period, at most 72 characters.
- The subject says **what**; the body, separated by a blank line, says **why** and what was considered. A body is optional for a one-line change and expected for anything a reviewer might question six months later.
- One logical change per commit. A commit that touches a migration, the entity copies and the UI for a single feature is fine; a commit that fixes two unrelated bugs is two commits.
- Do not combine unrelated subjects with "and" as some older commits do; split instead.
- Reference the issue in the body (`Closes #123`), not in the subject.

Example:

```
require a supported country on every place

The reward currency is derived from the place's country, so a place without
a country would leave the item with no currency. Both APIs now reject an
unsupported code with BAD_REQUEST and the admin form shows it as a field error.

Closes #87
```

## What never goes in

- Secrets, API keys, tokens. Dev credentials for local containers (`derechi`/`derechi`) are not secrets; a real key is, even in a default value. See [configuration](configuration.md#rule-7-no-secrets-in-the-repository).
- Build output: `target/`, IDE folders. The root `.gitignore` covers the Maven side; do not add ignore files inside Maven modules. `Web-Client` keeps its own `.gitignore` (`node_modules/`, `.next/`) because it is a separate pnpm project, not a module of the reactor.
- Generated Liquibase deltas named `000-delta.postgresql.sql`. Rename and review them before committing; see [database-migrations](database-migrations.md).
- Realm exports with personal data. The committed `derechi-realm.json` contains only dev users.
- Large binaries. Images used by the admin UI are served from MinIO, not from the repository.

## Tags and releases

Releases are tagged on `main` as `vMAJOR.MINOR.PATCH`. The procedure is in [release](../processes/release.md).
