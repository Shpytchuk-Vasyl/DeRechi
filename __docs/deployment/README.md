# Deployment

DeRechi runs in two modes. In **development mode** only the infrastructure (PostgreSQL, pgAdmin, RabbitMQ, Keycloak, MinIO, Mailpit, Prometheus, Alertmanager, Grafana) runs in Docker, started with `docker compose up -d`, and the Spring services are started from IDEA or Maven on the host. In **full mode** the services are built into images and run in the same compose project, started with `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build`. The second file is an override of the first, not a standalone compose file. **Production** runs the same two files with an `env.prod` file: the committed defaults are production-safe, and what only development wants (Keycloak's `start-dev`, the dev users) is commented out for a developer to uncomment locally.

- [Local development](local-development.md) - prerequisites, first start, start order, URLs and dev users.
- [Docker Compose](docker-compose.md) - the compose files and overrides, anchors, healthchecks and memory limits, volumes, resetting data.
- [Docker image](docker-image.md) - the shared two-stage `Dockerfile`, the `MODULE` build argument, the custom PostGIS image.
- [Environment variables](environment-variables.md) - every variable a container reads, per service, and the `env.local` / `env.test` / `env.prod` files.
- [Infrastructure](infrastructure.md) - ports, credentials and consoles of the supporting services.
- [Keycloak](keycloak.md) - the realm file, clients, dev users, exporting changes.
- [Monitoring](monitoring.md) - Prometheus scrape configs, alerts and Alertmanager, dead-letter queues, Grafana, actuator endpoints.

Production runs the same compose stack with `env.prod` (Keycloak in `start` mode without dev users, TLS at a reverse proxy in front of the host). There is no CI and no staging environment yet.
