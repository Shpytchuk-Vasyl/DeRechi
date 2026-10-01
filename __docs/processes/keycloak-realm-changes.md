# Keycloak realm changes

The `derechi` realm lives in Git as `docker/keycloak/realms/derechi-realm.json`. The Keycloak
container starts with `--import-realm`, which reads that file **only when the realm does not
exist yet**, in practice only on the first start against an empty `keycloak` database. After
that, the file and the running realm drift apart silently: editing the JSON changes nothing,
and clicking in the console changes nothing in Git.

Any change to roles, composite roles, clients, redirect URIs or dev users has to end up in the
JSON, because that is what every new environment and every teammate's fresh checkout starts
from. Two ways to get there.

What the realm contains and how the roles map to code is in
[deployment/keycloak.md](../deployment/keycloak.md). The full checklist for a new admin
permission is in [add an admin permission](../extending/add-an-admin-permission.md).

## Option A: edit the JSON, re-import

Best for small, well-understood edits: a new `SCOPE:ACTION` client role on `derechi-admin`,
adding it to a composite, a new redirect URI.

1. Edit `docker/keycloak/realms/derechi-realm.json`. Follow the shape of the neighbouring
   entries; client roles for the admin panel are named exactly `SCOPE:ACTION` and the
   composites `ADMIN_VIEWER`, `ADMIN_EDITOR`, `ADMIN_SUPER` list them.
2. Throw away the running realm so the import runs again. Either reset only Keycloak:

   ```bash
   docker compose stop keycloak
   docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE keycloak;"
   docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE keycloak OWNER derechi;"
   docker compose up -d keycloak
   ```

   or reset everything, which also wipes your `derechi` data, RabbitMQ and MinIO:

   ```bash
   docker compose down -v
   docker compose up -d
   ```

3. Log into the admin panel with a dev user that should have the new role and confirm. The
   mapper logs the resolved authorities at `DEBUG` (`KeycloakAuthoritiesMapper`), which is the
   quickest way to see what the token actually carries.

## Option B: change it in the console, export

Best for anything you would rather click through than hand-write: a new client with its
scopes and mappers, protocol settings, anything with generated ids.

1. Make the change at `http://localhost:8180` (`admin` / `admin`), realm `derechi`.
2. Export the realm from inside the container. The flags below are for Keycloak 26; check
   `kc.sh export --help` on the running version if they are rejected:

   ```bash
   docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
       --dir /opt/keycloak/data/export --realm derechi --users realm_file
   docker compose cp keycloak:/opt/keycloak/data/export/derechi-realm.json docker/keycloak/realms/
   ```

   The export runs against the same database as the server; if it complains about the
   running instance, stop the container and run the export with the `KC_DB_*` variables from
   `docker-compose.yml` set.

3. Review the diff before committing. Exports are noisy: generated ids, timestamps, default
   client scopes and built-in clients move around. Keep the diff down to what you changed where
   you reasonably can, and make sure nothing secret was exported (the `derechi-services`
   client is confidential; its secret must not be a real one).
4. Keep the dev users (`admin@derechi.local`, `moderator@derechi.local`,
   `viewer@derechi.local`, `user@derechi.local`) with their documented passwords; the whole
   team and the docs rely on them.
5. Confirm the file still imports: do the reset from Option A step 2 and log in.

## Shipping the change

- Commit the realm JSON **in the same PR** as the code that needs the new role or client, and
  say so in the PR description. The reviewer checks that the `Scope`/`Action` enums, the
  `PermissionsTest` expectations and the JSON agree.
- On a shared environment the import will not run again. Apply the same change there by hand
  in the console, or re-import if the environment can afford losing its users. This is a
  manual step in the [release order](release.md).
- A removed role is not removed from users who already have it in a running realm; clean that
  up in the console too.
