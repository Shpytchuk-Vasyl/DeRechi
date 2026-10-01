# Discovery

The Eureka server. Every other Spring service registers here on startup, and the gateway
resolves `lb://CLIENT-API` through it.

| | |
|---|---|
| Port | 8761 |
| Dependencies | `spring-cloud-starter-netflix-eureka-server` only |
| Needs | nothing; start it first |
| Dashboard | `http://localhost:8761` |

## Config

```yaml
eureka:
  client:
    register-with-eureka: false   # the registry does not register itself
    fetch-registry: false         # and does not pull a registry
  server:
    enable-self-preservation: false
```

Self-preservation is off because on a development machine instances come and go all the
time. With it on, Eureka would notice the renewals dropping below its threshold, stop
evicting instances, and the gateway would keep routing to services that are no longer
there. In a stable production cluster the setting would be reconsidered.

Clients point at it with `eureka.client.service-url.defaultZone`, which is
`http://localhost:8761/eureka` in every `application.yaml` and
`EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://discovery:8761/eureka` in Compose. Clients
register with `prefer-ip-address: true` so that container hostnames do not leak into the
registry.

## In Docker

`discovery` is the one service in `docker-compose.services.yml` that does not use the
shared `x-service-base` anchor, because that anchor has a `depends_on: discovery` and the
registry cannot depend on itself.

## Tests

`DiscoveryApplicationTests` loads the context.
