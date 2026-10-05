# Keycloak

Keycloak is the identity provider for the admin panel and, in future, for the public client. There is no auth module of our own. How the roles are mapped into Spring Security is in [authentication](../architecture/authentication.md) and [permissions](../features/permissions.md); this page is about running and maintaining the Keycloak instance. The procedure for changing the realm is in [keycloak-realm-changes](../processes/keycloak-realm-changes.md).

## The realm file

`docker/keycloak/realms/derechi-realm.json` is mounted into `/opt/keycloak/data/import` and loaded by `start-dev --import-realm`. Two things to know:

1. **Import happens only when the realm does not exist yet**, that is on the first start with an empty `pgdata` volume. Restarting the container does not re-import. To pick up a changed file: `docker compose down -v` (loses all local data) or delete the realm in the console and restart.
2. **Changes made in the console do not flow back to the file.** A role, client or user created through the UI lives in the `keycloak` database only. If it should be permanent, export it and commit the file.

### Exporting

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
    --dir /tmp/export --realm derechi --users realm_file
docker compose cp keycloak:/tmp/export/derechi-realm.json docker/keycloak/realms/derechi-realm.json
```

This is the Keycloak 26 syntax (`--users realm_file` puts the users into the same file as the realm). Adjust if your version differs. Review the diff before committing: an export rewrites ids and ordering, so look for the change you meant to make and discard noise where you can. Never commit an export that contains real user data; the file holds only the four dev users.

## Database

Keycloak manages its own schema and lives in the separate `keycloak` database of the same PostgreSQL container (`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`, created by `docker/postgres/initdb/01-keycloak.sh`). The rule that only `DB-Postgres` changes the schema does not apply to it; Keycloak migrates itself on upgrade.

## Clients

| Client | Type | Flow | Redirect URIs | Used by |
|---|---|---|---|---|
| `derechi-admin` | public | Authorization Code + PKCE | `http://localhost:8083/*` | `Admin-API` login (`spring.security.oauth2.client`) |
| `derechi-web` | public | Authorization Code + PKCE | `http://localhost:8080/*`, `:3000/*`, `:5173/*` | the public web client, when it gets authentication |
| `derechi-services` | confidential | `client_credentials` | none | reserved for service-to-service calls; not used by any module yet |

`Admin-API` requests the scopes `openid,profile,email,roles` and uses `preferred_username` as the user name. No module is a resource server today; the only integration is the OIDC login in the admin panel.

## Roles

Realm roles: `USER`, `ADMIN`, `ADMIN_VIEWER`, `ADMIN_EDITOR`, `ADMIN_SUPER`. The three `ADMIN_*` roles are composites of client roles of `derechi-admin`, which have the shape `SCOPE:ACTION` (`LOST_ITEM:EDIT`, `MATCH:NOTIFY`, ...). `KeycloakAuthoritiesMapper` turns realm roles into `ROLE_*` authorities and client roles into authorities as they are. Adding a permission therefore means adding a client role and attaching it to the right composite; see [add-an-admin-permission](../extending/add-an-admin-permission.md).

## Dev users

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

## Gateway route

Keycloak is not a Eureka client, so the Gateway routes `/realms/**` and `/resources/**` to it with a direct `uri: ${KEYCLOAK_URI}` rather than `lb://`. That route exists for the web client's login flow through port 8080; the admin panel talks to port 8180 directly.

## Metrics and health

`KC_HEALTH_ENABLED` and `KC_METRICS_ENABLED` are on. Both are served on the management port 9000 inside the container (`/health`, `/metrics`), which is not published to the host. Prometheus scrapes `keycloak:9000/metrics` as its own job, because the path is not the actuator path the service job uses.
