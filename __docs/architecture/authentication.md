# Authentication

There is no home-grown auth module. Keycloak is the identity provider for everyone, and the
only piece of the system that talks to it today is the admin panel's login.

## Keycloak

Keycloak 26.4 runs as a container (`derechi-keycloak`, port 8180 on the host, 8080 inside
the Compose network). `docker-compose.yml` runs it in `start-dev` mode (plain HTTP, dev
users imported); the production override `docker-compose.prod.yml` runs `start --import-realm`
behind a TLS reverse proxy (`KC_HTTP_ENABLED`, `KC_PROXY_HEADERS=xforwarded`) and mounts only
the realm file. Its data lives in the `keycloak` database of the shared PostgreSQL container,
see [database.md](database.md).

The realm `derechi` is versioned in `docker/keycloak/realms/derechi-realm.json` and loaded
with `--import-realm`. The import happens **only when the realm does not exist yet**, which
in practice means the first start on a fresh volume. Anything changed later in the admin
console stays in the database and is not written back to the file. The procedure for
changing the realm and keeping git in sync is in
[../processes/keycloak-realm-changes.md](../processes/keycloak-realm-changes.md).

The realm has self-registration off (`registrationAllowed: false`) and brute-force
protection on (`bruteForceProtected: true`). The file holds no users and no real secrets:
`${DERECHI_SITE_URL}`, `${DERECHI_ADMIN_URL}` and `${KEYCLOAK_SERVICES_SECRET}` are
placeholders that Keycloak fills from its environment during the import.

Admin console: `http://localhost:8180`, `admin` / `admin`.

### Clients

| Client | Type | Flow | Used by |
|---|---|---|---|
| `derechi-web` | public | Authorization Code + PKCE (S256); direct access grants off | the public web client, reserved for when it gets a login |
| `derechi-admin` | public | Authorization Code + PKCE (S256), scopes `openid,profile,email,roles` | `Admin-API` login |
| `derechi-services` | confidential | `client_credentials` (service account) | reserved for service-to-service calls; nothing uses it yet |

Redirect URIs in the realm file are placeholders: `${DERECHI_ADMIN_URL}/*` for the admin
panel and `${DERECHI_SITE_URL}/*` for the web client (also used as post-logout redirect
URIs). `docker-compose.yml` defaults them to `http://localhost:8083` and
`http://localhost:3000`; the production override requires them to be set. They are read on
the first import only, so changing them later is done in the admin console.

### Roles

Realm roles: `USER`, `ADMIN`, `ADMIN_VIEWER`, `ADMIN_EDITOR`, `ADMIN_SUPER`.

Client roles on `derechi-admin` are the permissions the admin panel checks, named
`SCOPE:ACTION`: `MATCH:VIEW`, `MATCH:NOTIFY`, `LOST_ITEM:VIEW`, `LOST_ITEM:CREATE`,
`LOST_ITEM:EDIT`, `LOST_ITEM:DELETE`, `LOST_ITEM:ARCHIVE`, the same set for `FOUND_ITEM`,
and `VIEW`/`CREATE`/`EDIT`/`DELETE` for `LOST_ITEM_HISTORY` and `FOUND_ITEM_HISTORY`.

The three `ADMIN_*` realm roles are composites that bundle client roles, each including the
previous one:

| Realm role | Adds |
|---|---|
| `ADMIN_VIEWER` | `MATCH:VIEW`, `LOST_ITEM:VIEW`, `FOUND_ITEM:VIEW` |
| `ADMIN_EDITOR` | `ADMIN_VIEWER` + `MATCH:NOTIFY`, `LOST_ITEM:CREATE`, `LOST_ITEM:EDIT`, `FOUND_ITEM:CREATE`, `FOUND_ITEM:EDIT` |
| `ADMIN_SUPER` | `ADMIN_EDITOR` + `ADMIN` + `LOST_ITEM:DELETE`, `FOUND_ITEM:DELETE`, `LOST_ITEM:ARCHIVE`, `FOUND_ITEM:ARCHIVE`, `LOST_ITEM_HISTORY:VIEW`, `FOUND_ITEM_HISTORY:VIEW` |

Note that the `*_HISTORY:CREATE/EDIT/DELETE` client roles exist but are not part of any
composite; a user would have to be given them directly. How the panel uses these is in
[../features/permissions.md](../features/permissions.md).

### Development users

They live in `docker/keycloak/dev/derechi-users-0.json`, which only `docker-compose.yml`
mounts next to the realm, so they are imported in development and never in production.

| User | Password | Realm roles |
|---|---|---|
| `admin@derechi.local` | `admin` | `ADMIN_SUPER`, `USER` |
| `moderator@derechi.local` | `moderator` | `ADMIN_EDITOR`, `USER` |
| `viewer@derechi.local` | `viewer` | `ADMIN_VIEWER`, `USER` |
| `user@derechi.local` | `user` | `USER` |

## Admin-API login

`Admin-API` is an OAuth2 **client** (OIDC login), not a resource server. The relevant
config is `spring.security.oauth2.client` in `Admin-API/src/main/resources/application.yaml`:
registration `keycloak` with `client-id: derechi-admin`,
`client-authentication-method: none` (public client), `authorization_code`, the standard
`{baseUrl}/login/oauth2/code/{registrationId}` redirect, and
`user-name-attribute: preferred_username`.

`SecurityConfig` requires authentication on everything except `/actuator/health(/**)`,
`/actuator/info`, `/actuator/prometheus` (`PUBLIC_ACTUATOR`; actuator itself is on the
management port 9083), `/css/**`, `/js/**` and `/error`, enables `oauth2Login()` with defaults, and wires logout to
`OidcClientInitiatedLogoutSuccessHandler` so that signing out of the panel also ends the
Keycloak session and lands on `{baseUrl}/admin`.

### From Keycloak roles to Spring authorities

`KeycloakAuthoritiesMapper` (`Admin-API/src/main/java/org/shpytchuk/adminapi/security/`) is
registered as the `GrantedAuthoritiesMapper`. For every `OidcUserAuthority` it reads the
claims of the ID token and of the userinfo response and adds:

- `realm_access.roles[*]` as `ROLE_<name>` (so `ADMIN_SUPER` becomes `ROLE_ADMIN_SUPER`);
- `resource_access.derechi-admin.roles[*]` as they are (`LOST_ITEM:EDIT`).

The client id it looks under comes from `derechi.admin.client-id`. Because the composites
are expanded by Keycloak before the token is issued, a user with `ADMIN_EDITOR` arrives with
all the client roles that composite contains.

This is a `GrantedAuthoritiesMapper` and not a `JwtAuthenticationConverter` because the
panel does an OIDC login; the JWT converter is the resource-server tool and there is no
resource server here.

### Two Keycloak URLs in Docker

In a browser-based login the browser is redirected to Keycloak and the server exchanges the
code with Keycloak. From inside the Compose network those are different addresses. The
`docker` Spring profile in `Admin-API` therefore does not use `issuer-uri` discovery and
spells the endpoints out instead:

| Endpoint | Base |
|---|---|
| `authorization-uri` | `KEYCLOAK_PUBLIC_URI` (default `http://localhost:8180`), what the browser can reach |
| `token-uri`, `jwk-set-uri`, `user-info-uri` | `KEYCLOAK_URI` (default `http://keycloak:8080`), container-internal |

Keycloak itself is started with `KC_HOSTNAME=http://localhost:8180` and
`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` so that the issuer in the token matches the public
URL while back-channel calls on the internal address are accepted. Outside Docker (the
default profile) a single `issuer-uri: http://localhost:8180/realms/derechi` is enough.

## Gateway and Keycloak

Keycloak is not behind the gateway. The gateway used to have a `keycloak` route
(`/realms/**`, `/resources/**`) that exposed the realm under its own origin, but nothing
used it and it was removed. Browsers reach Keycloak directly at `KEYCLOAK_PUBLIC_URI`
(`http://localhost:8180` locally), and `Admin-API` talks to it on the internal
`KEYCLOAK_URI`, as described above. See [modules/getaway.md](modules/getaway.md).

## What is not there yet

- No resource server: `Client-API` is unauthenticated, and the gateway does not validate
  tokens. The `derechi-web` client and the `USER` role exist for the day it does.
- `derechi-services` has no caller. Service-to-service traffic is RabbitMQ, which has its
  own credentials.
- Metrics: Keycloak exposes `/metrics` on its management port 9000, scraped as a separate
  Prometheus job. See [observability.md](observability.md).
