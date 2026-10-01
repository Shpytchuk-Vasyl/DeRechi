# Authentication

There is no home-grown auth module. Keycloak is the identity provider for everyone, and the
only piece of the system that talks to it today is the admin panel's login.

## Keycloak

Keycloak 26.4 runs as a container (`derechi-keycloak`, port 8180 on the host, 8080 inside
the Compose network) in `start-dev` mode. Its data lives in the `keycloak` database of the
shared PostgreSQL container, see [database.md](database.md).

The realm `derechi` is versioned in `docker/keycloak/realms/derechi-realm.json` and loaded
with `--import-realm`. The import happens **only when the realm does not exist yet**, which
in practice means the first start on a fresh volume. Anything changed later in the admin
console stays in the database and is not written back to the file. The procedure for
changing the realm and keeping git in sync is in
[../processes/keycloak-realm-changes.md](../processes/keycloak-realm-changes.md).

Admin console: `http://localhost:8180`, `admin` / `admin`.

### Clients

| Client | Type | Flow | Used by |
|---|---|---|---|
| `derechi-web` | public | Authorization Code + PKCE (S256); direct access grants also on | the public web client, reserved for when it gets a login |
| `derechi-admin` | public | Authorization Code + PKCE (S256), scopes `openid,profile,email,roles` | `Admin-API` login |
| `derechi-services` | confidential | `client_credentials` (service account) | reserved for service-to-service calls; nothing uses it yet |

Redirect URIs in the realm file are `localhost` ones (`http://localhost:8083/*` for the
admin panel, 8080/3000/5173 for the web client). A deployment on another host needs those
changed.

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

`SecurityConfig` requires authentication on everything except `/actuator/**`, `/css/**`,
`/js/**` and `/error`, enables `oauth2Login()` with defaults, and wires logout to
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

Keycloak does not register with Eureka, so the gateway route to it is a direct `uri:`
(`KEYCLOAK_URI`, default `http://localhost:8180`), matching `/realms/**` and
`/resources/**`. That exposes the realm's OIDC endpoints and login theme assets under the
gateway's origin for the web client. See [modules/getaway.md](modules/getaway.md).

## What is not there yet

- No resource server: `Client-API` is unauthenticated, and the gateway does not validate
  tokens. The `derechi-web` client and the `USER` role exist for the day it does.
- `derechi-services` has no caller. Service-to-service traffic is RabbitMQ, which has its
  own credentials.
- Metrics: Keycloak exposes `/metrics` on its management port 9000, scraped as a separate
  Prometheus job. See [observability.md](observability.md).
