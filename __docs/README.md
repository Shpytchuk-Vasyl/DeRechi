# DeRechi documentation

DeRechi is a lost-and-found service. People post notices about things they lost or found
through the public web client, which talks to a GraphQL API. Moderators work in an admin panel
where they maintain notices and see, for every lost item, the found items that look like a
match. Matching happens automatically in the background whenever a notice is created, and a
moderator can notify the owner of a lost item about a promising candidate by email, SMS or a
messenger with one click.

Everything runs as a set of Spring Boot services behind a gateway, sharing one PostgreSQL
database and talking to each other through RabbitMQ. Keycloak handles logins, MinIO stores
photos, Prometheus and Grafana watch the whole thing.

## Start here

New to the project? Read [Getting started](developer-guide/getting-started.md) to get the
stack running on your machine, then [Architecture](architecture/README.md) to understand what
you just started.

## What is where

| Folder | What you will find |
|---|---|
| [architecture/](architecture/README.md) | How the system is put together: modules, database, messaging, authentication, storage, observability. One page per module under `architecture/modules/`. |
| [conventions/](conventions/README.md) | How we write code, configuration, migrations, translations and tests, and how we use Git. |
| [deployment/](deployment/README.md) | Running the stack locally and in containers, environment variables, infrastructure services, Keycloak and monitoring. |
| [features/](features/README.md) | What the product does today, feature by feature, with pointers to the code that implements it. |
| [extending/](extending/README.md) | Step-by-step checklists for the changes we make often: a new language, country, permission, social network, notification channel, event, module. |
| [processes/](processes/README.md) | Team processes: code review, database changes, releases, Keycloak realm changes, definition of done. |
| [developer-guide/](developer-guide/README.md) | The day-to-day workflow from ticket to production: branching, testing, pull requests, troubleshooting. |

## Docs that live elsewhere

Two modules carry their own README because the content is tightly bound to the module:

- [DB-Postgres/README.md](../DB-Postgres/README.md) is the full Liquibase reference: every
  Maven goal, the known quirks of the diff generator, recovery commands.
- [Web-Client/README.md](../Web-Client/README.md) documents the Next.js client, which is not
  part of the Maven reactor.

