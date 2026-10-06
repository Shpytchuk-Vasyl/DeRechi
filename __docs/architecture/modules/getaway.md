# Getaway

The public entry point, a Spring Cloud Gateway on WebFlux. Its job is narrow: get the web
client's traffic to `Client-API` and move files in and out of MinIO. It is the one reactive module in the reactor; everything
else is blocking code on virtual threads.

The module is named `Getaway` (not `Gateway`); the directory, `artifactId`, `<module>` entry
and `spring.application.name` all use that spelling, and so does the Dockerfile. Keep it.

| | |
|---|---|
| Port | 8080; actuator on the management port 9080 |
| Dependencies | `spring-cloud-starter-gateway-server-webflux`, `spring-cloud-starter-circuitbreaker-reactor-resilience4j` |
| Needs | Client-API and MinIO reachable at the configured URIs |
| Code | `GetawayApplication` and `application.yaml`; no Java beyond the main class |

## Routes

Defined in `Getaway/src/main/resources/application.yaml` under
`spring.cloud.gateway.server.webflux.routes`.

| Id | Match | Target | Filters |
|---|---|---|---|
| `client-api` | `/graphql/**`, `/graphiql/**`, `/api/client/**` | `${CLIENT_API_URI:http://localhost:8082}` | none |
| `files-upload` | `PUT /${MINIO_BUCKET:derechi-files}/**` | `${MINIO_URI:http://localhost:9000}` | `PreserveHostHeader`, `DedupeResponseHeader` on the CORS headers, `RequestSize` `${MINIO_MAX_UPLOAD:5MB}` |
| `files` | `GET /files/**` | `${MINIO_URI:http://localhost:9000}` | `RewritePath` `/files/(?<key>.*)` to `/derechi-files/${key}`, `AddResponseHeader Cache-Control: public, max-age=31536000, immutable` |

There used to be a `keycloak` route (`/realms/**`, `/resources/**`) that exposed the realm
under the gateway's origin. Nothing used it, so it was removed: browsers reach Keycloak
directly, see [../authentication.md](../authentication.md#gateway-and-keycloak).

Every route is a direct `uri:` from a variable whose default is the local address; compose
sets the container address (`http://client-api:8082`). There is no service registry since
the `Discovery` module was removed: on one compose host the container name is resolved by
Docker's DNS, and today several `Client-API` instances would need a load balancer (or a
Kubernetes Service) behind that one address. Planned: bring service discovery back, with
`lb://` routes.

`Admin-API` is intentionally not routed here. It is a server-rendered application with its
own session and OIDC redirect URIs registered for `${DERECHI_ADMIN_URL}` (`localhost:8083`
locally); putting it behind the
gateway would only add a second origin to keep in sync with Keycloak.

The upload route and the read route are explained end to end in
[../file-storage.md](../file-storage.md).

## CORS

Global CORS is configured for all paths (`'[/**]'`) with
`add-to-simple-url-handler-mapping: true` so preflights on unknown paths get an answer too.
Allowed origins come from `WEB_ORIGIN_PATTERNS`, defaulting to localhost, 127.0.0.1 and the
private `192.168.*` / `10.*` ranges on any port. The private ranges are there so that a
phone on the same network can use the web client against a developer machine. Credentials
are allowed, all headers, the usual methods, one-hour max age.

## Config and environment

| Variable | Default | Meaning |
|---|---|---|
| `MINIO_URI` | `http://localhost:9000` | MinIO base for both file routes |
| `MINIO_BUCKET` | `derechi-files` | bucket name used in both file routes |
| `MINIO_MAX_UPLOAD` | `5MB` | body cap on the presigned PUT |
| `WEB_ORIGIN_PATTERNS` | localhost and LAN patterns | CORS allow list |
| `CLIENT_API_URI` | `http://localhost:8082` | target of the `client-api` route |
| `MANAGEMENT_PORT` | `9080` | actuator port |

In Compose the gateway waits for `minio` to be healthy.

## Actuator

`health`, `info` and `prometheus`, on the management port 9080, which Compose does not
publish; no route forwards `/actuator`. Health shows no details (`show-details: never`).
The `gateway` endpoint is not exposed any more: it could add and delete routes at runtime.
To check the routes of a running gateway, read its `application.yaml` and the environment
of the container (a `404` on a PUT to the bucket means the upload route does not match).

## Tests

`GetawayApplicationTests` loads the context with `reactor-test` on the classpath. Route
behaviour is exercised manually and from the web client's upload tests, see
`Web-Client/README.md`.
