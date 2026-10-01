# Branches and commits

The Git rules in short. The reasoning and the few hard rules are in
[conventions/git.md](../conventions/git.md); this page is the daily usage.

## Branches

`main` is the only long-lived branch and it is always deployable. Everything else is a
short-lived branch off `main`, merged back through a pull request and deleted.

Name branches by kind and topic, lowercase kebab-case, with the ticket id when there is one:

| Kind | Example | For |
|---|---|---|
| `feature/` | `feature/42-signal-channel` | New behaviour |
| `fix/` | `fix/57-dlq-on-unknown-routing-key` | Bug fixes |
| `chore/` | `chore/spring-boot-4-1-2` | Dependencies, build, tooling, no behaviour change |
| `..` | `..` | Documentation only |

Keep branches short: a few days, not weeks. If a task is bigger, split it into PRs that each
leave `main` working, for example the migration first and the feature after.

Rebase on `main` before opening the PR and whenever `main` moves under you during review:

```bash
git fetch origin
git rebase origin/main
git push --force-with-lease
```

`--force-with-lease` is fine on your own branch. Never force-push `main`.

## Commit messages

The history reads as a list of changes, so write each subject as one:

- imperative mood, lowercase first word, no trailing period, at most 72 characters;
- say what the change does, not what you did ("add", "fix", "remove", "deliver");
- a body when the *why* is not obvious: what was wrong, why this fix, what was considered.

Good subjects from our own history:

```
add support for place search
deliver match notifications only through the requested channel
drop the messenger fan-out until recipients can be addressed
remove commented-out macOS PostGIS image reference from Dockerfile
```

Less good, also from our history, and why:

```
 fix the admin UI with file dropzone      <- leading space, and "fix" of what?
update getaway config for local crossmachine dev fix cors issue   <- two changes, one commit
```

A body looks like this:

```
deliver match notifications only through the requested channel

NotifyChannel used to be ignored by the event, so choosing "email" still
sent an SMS. The event now carries null for contacts that were not
requested; Notification skips channels without a recipient.
```

Commit as you go on the branch; squash at merge keeps `main` clean, so intermediate "wip"
commits are fine as long as the PR title is a proper subject.

## What not to commit

- `target/`, `node_modules/`, `.next/`: build output, already in `.gitignore`.
- IDE state: `.idea/workspace.xml`, `*.iml`, run configurations with local paths.
- Secrets and personal keys. Local defaults for dev containers (`derechi` / `derechi`) are
  fine; anything that works against a real service is not. The Google Maps key in
  `Admin-API/application.yaml` should be overridden through `GOOGLE_MAPS_API_KEY`, not edited.
- `000-delta.postgresql.sql` or any unreviewed diff output under `DB-Postgres/changelog/changes`.
- Modules' own `mvnw`, `.mvn/`, `HELP.md`, `.gitignore`, `.gitattributes`: Initializr
  generates them, we delete them. The wrapper lives in the root only.
- The `Launcher` entry in the root `<modules>` uncommented, if you enabled it locally.

## Line endings and case

Git Bash on Windows hides case differences in file names; Docker builds on Linux do not. A
module folder, its `artifactId` and its `<module>` entry must match letter for letter. Keep
`mvnw` with LF line endings; a CRLF wrapper fails inside the Docker build stage.
