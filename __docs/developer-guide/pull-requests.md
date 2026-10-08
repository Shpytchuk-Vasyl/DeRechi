# Pull requests

A pull request is how a change gets a second pair of eyes and how we later find out why
something was done. Write it for the reviewer today and for the person reading `git log`
in a year.

## Before opening

- [ ] Branch rebased on `main`, build and tests green locally with Docker running.
- [ ] You read your own diff on GitHub once. Leftover debug logging, commented-out code and
      accidental file changes get caught here, not by the reviewer.
- [ ] Docs updated for what changed.

## Title

Same rules as a commit subject ([branches and commits](branches-and-commits.md)): imperative,
lowercase, under 72 characters. With squash merge, this becomes the commit on `main`.

```
add signal as a notification channel
fix duplicate dialog ids after loading more candidates
```

## Description template

Copy this into the PR body and delete the sections that do not apply, except "How to test",
which always applies.

```markdown
## What
One paragraph: the change as the user or the next developer sees it.

## Why
The ticket, the bug, the reason this approach and not another. Link: #123

## How to test
Steps a reviewer can follow on their machine. Which user, which page, which query,
what they should see. Paste the GraphQL document if there is one.

## Migration
Changeset file name; whether it backfills data; whether old services keep working.

## Keycloak
Roles or clients changed in `docker/keycloak/realms/derechi-realm.json`; dev users changed in
`docker/keycloak/dev/derechi-users-0.json`.

## RabbitMQ
Exchanges, queues, bindings changed in `docker/rabbitmq/definitions.json`.

## Web-Client impact
Schema fields added or changed, files route changes, anything the client must regenerate
or adapt. "None" if none.

## Screenshots
For admin UI changes: before and after, in at least one non-English locale.

## Checklist
(paste docs/processes/definition-of-done.md and tick)
```

## Size and scope

- Aim for under roughly 400 changed lines of hand-written code. Message bundles, migrations
  and generated files do not count, but say so if they dominate the diff.
- One concern per PR. A refactoring that you needed first goes in its own PR, merged first.
- Open a **draft** PR when you want feedback on direction before polishing. Mark it ready
  when the checklist is honestly ticked.

## During review

- Request one reviewer; two for migrations, security configuration or the Keycloak realm.
- Push fixes as new commits so the reviewer sees what moved; do not rewrite history
  mid-review unless you are rebasing on `main`.
- Reply to every comment and resolve the ones you addressed. Disagreement is fine; take it to
  a call if it exceeds two rounds and write the outcome on the PR.
- Re-request review after addressing a blocking comment.

## Merging

- Needs at least one approval and an up-to-date branch. See
  [code review](../processes/code-review.md) for the approval rules.
- **Squash merge** by default; the PR title and description become the commit. Use rebase
  merge only when each commit builds and is meant to stand alone.
- The author merges, then deletes the branch (GitHub can do this automatically).
- After merge, verify on staging (for now, the full compose stack) and close the ticket. See
  [development workflow](development-workflow.md), steps 12 and 13.

## What we do not have yet

The `tests` workflow (`.github/workflows/tests.yml`) runs `./mvnw test` of the shipped modules (all
but `Admin-API`) and then the integration tests, see [stack tests](stack-tests.md#ci). For now it
runs only on demand: the pull-request trigger is commented out, so it is not a status check, and
branch protection on `main` (green `tests` and one approval) is still the plan. Until then the
author runs it for the branch and the reviewer checks that the run is green before approving.
