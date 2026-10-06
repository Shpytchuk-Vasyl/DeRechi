# Keycloak

Keycloak is the identity provider for the admin panel and, in future, for the public client. There is no auth module of our own. How the roles are mapped into Spring Security is in [authentication](../architecture/authentication.md) and [permissions](../features/permissions.md); this page is about running and maintaining the Keycloak instance. The procedure for changing the realm is in [keycloak-realm-changes](../processes/keycloak-realm-changes.md).

## The realm file

`docker/keycloak/realms/derechi-realm.json` is mounted into `/opt/keycloak/data/import` and loaded by `--import-realm`: `start-dev` locally (`docker-compose.yml`), `start` in production (`docker-compose.prod.yml`, behind a TLS-terminating reverse proxy with `KC_HTTP_ENABLED=true` and `KC_PROXY_HEADERS=xforwarded`). The file holds **no users**; the dev users are a separate file, see [Dev users](#dev-users). Things to know:

1. **Import happens only when the realm does not exist yet**, that is on the first start with an empty `pgdata` volume. Restarting the container does not re-import. To pick up a changed file: `docker compose down -v` (loses all local data) or delete the realm in the console and restart.
2. **Changes made in the console do not flow back to the file.** A role, client or user created through the UI lives in the `keycloak` database only. If it should be permanent, export it and commit the file.
3. **The file has placeholders**, filled from the container's environment on import: `${DERECHI_SITE_URL}` (redirect and post-logout URIs of `derechi-web`), `${DERECHI_ADMIN_URL}` (the same for `derechi-admin`) and `${KEYCLOAK_SERVICES_SECRET}` (the secret of `derechi-services`). Compose passes them with the local defaults `http://localhost:3000`, `http://localhost:8083` and `dev-secret-change-me`; `docker-compose.prod.yml` requires them.

### Exporting

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
    --dir /tmp/export --realm derechi --users skip
docker compose cp keycloak:/tmp/export/derechi-realm.json docker/keycloak/realms/derechi-realm.json
```

This is the Keycloak 26 syntax (`--users skip` leaves the users out). Adjust if your version differs. Do not export with `--users realm_file`: that puts the users into the realm file, and users must never end up there, because production imports it. A change to the dev users goes into `docker/keycloak/dev/derechi-users-0.json` by hand.

An export writes the **literal values** in place of the placeholders (the local URLs, the dev secret). Put `${DERECHI_SITE_URL}`, `${DERECHI_ADMIN_URL}` and `${KEYCLOAK_SERVICES_SECRET}` back before committing. Review the rest of the diff too: an export rewrites ids and ordering, so look for the change you meant to make and discard noise where you can.

## Database

Keycloak manages its own schema and lives in the separate `keycloak` database of the same PostgreSQL container (`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`, created by `docker/postgres/initdb/01-keycloak.sh`). The rule that only `DB-Postgres` changes the schema does not apply to it; Keycloak migrates itself on upgrade.

## Clients

| Client | Type | Flow | Redirect URIs | Used by |
|---|---|---|---|---|
| `derechi-admin` | public | Authorization Code + PKCE | `${DERECHI_ADMIN_URL}/*` | `Admin-API` login (`spring.security.oauth2.client`) |
| `derechi-web` | public | Authorization Code + PKCE | `${DERECHI_SITE_URL}/*` | the public web client, when it gets authentication |
| `derechi-services` | confidential | `client_credentials` | none; secret `${KEYCLOAK_SERVICES_SECRET}` | reserved for service-to-service calls; not used by any module yet |

Direct access grants (the password grant) are off on every client. The realm has self-registration off (`registrationAllowed: false`) and brute-force protection on (`bruteForceProtected`, lockout after 5 failures).

`Admin-API` requests the scopes `openid,profile,email,roles` and uses `preferred_username` as the user name. No module is a resource server today; the only integration is the OIDC login in the admin panel.

## Roles

Realm roles: `USER`, `ADMIN`, `ADMIN_VIEWER`, `ADMIN_EDITOR`, `ADMIN_SUPER`. The three `ADMIN_*` roles are composites of client roles of `derechi-admin`, which have the shape `SCOPE:ACTION` (`LOST_ITEM:EDIT`, `MATCH:NOTIFY`, ...). `KeycloakAuthoritiesMapper` turns realm roles into `ROLE_*` authorities and client roles into authorities as they are. Adding a permission therefore means adding a client role and attaching it to the right composite; see [add-an-admin-permission](../extending/add-an-admin-permission.md).

## Dev users

The dev users live in `docker/keycloak/dev/derechi-users-0.json`, next to the realm file in the import directory but mounted only by `docker-compose.yml`. `docker-compose.prod.yml` replaces the volumes with the realm file alone, so production starts without them. Since the import runs only for a realm that does not exist yet, an instance that once imported the dev realm keeps these users until they are deleted by hand.

| User | Password | Roles |
|---|---|---|
| `admin@derechi.local` | `admin` | `ADMIN_SUPER`, `USER` |
| `moderator@derechi.local` | `moderator` | `ADMIN_EDITOR`, `USER` |
| `viewer@derechi.local` | `viewer` | `ADMIN_VIEWER`, `USER` |
| `user@derechi.local` | `user` | `USER` |

The console admin (`admin` / `admin`, from `KC_BOOTSTRAP_ADMIN_*`) is a master-realm user and cannot log into the admin panel.

## Addresses: one Keycloak, two hostnames

In development mode everything is `http://localhost:8180/realms/derechi`: the browser and `Admin-API` on the host both reach the published port, and the YAML uses a single `issuer-uri`.

In full mode `Admin-API` runs inside the Docker network and must call `http://keycloak:8080` for tokens, JWKS and user info, while the user's browser must still be redirected to `http://localhost:8180`. Two settings make this work:

- On the Spring side, the `docker` profile splits the provider into `authorization-uri` (`KEYCLOAK_PUBLIC_URI`) and `token-uri` / `jwk-set-uri` / `user-info-uri` (`KEYCLOAK_URI`).
- On the Keycloak side, `KC_HOSTNAME=http://localhost:8180` makes issued tokens carry the public issuer, and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` lets backchannel requests arrive on `keycloak:8080` without being rejected for a hostname mismatch. Without the second flag the token exchange from `Admin-API` fails because the issuer in the token (`localhost:8180`) does not match the address it was fetched from.

To reach the stack from another machine set `KEYCLOAK_PUBLIC_URI` in the host environment before `docker compose up`; see [environment-variables](environment-variables.md).

## No Gateway route

The Gateway no longer routes `/realms/**` and `/resources/**` to Keycloak. Browsers reach Keycloak directly at `KEYCLOAK_PUBLIC_URI` (port 8180 locally), and `Admin-API` in full mode reaches it inside the network at `KEYCLOAK_URI` (see above). In production put Keycloak behind the reverse proxy on its own hostname and set `KEYCLOAK_PUBLIC_URI` to that `https` address.

## Metrics and health

`KC_HEALTH_ENABLED` and `KC_METRICS_ENABLED` are on. Both are served on the management port 9000 inside the container (`/health`, `/metrics`), which is not published to the host. Prometheus scrapes `keycloak:9000/metrics` as its own job, because the path is not the actuator path the service job uses.
