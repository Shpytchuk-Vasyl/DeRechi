# Deployment

DeRechi runs in two modes. In **development mode** only the infrastructure (PostgreSQL, RabbitMQ, Keycloak, MinIO, Mailpit, Prometheus, Grafana) runs in Docker, started with `docker compose up -d`, and the Spring services are started from IDEA or Maven on the host. In **full mode** the services are built into images and run in the same compose project, started with `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build`. The second file is an override of the first, not a standalone compose file.

- [Local development](local-development.md) - prerequisites, first start, start order, URLs and dev users.
- [Docker Compose](docker-compose.md) - the two compose files, why the second is an override, anchors, volumes, resetting data.
- [Docker image](docker-image.md) - the shared two-stage `Dockerfile`, the `MODULE` build argument, the custom PostGIS image.
- [Environment variables](environment-variables.md) - every variable a container reads, per service, and the `env.local` / `env.test` / `env.prod` files.
- [Infrastructure](infrastructure.md) - ports, credentials and consoles of the supporting services.
- [Keycloak](keycloak.md) - the realm file, clients, dev users, exporting changes.
- [Monitoring](monitoring.md) - Prometheus scrape configs, Grafana, actuator endpoints.

There is no staging or production deployment described here yet; the compose setup is what we run locally and what a first server deployment would start from.
