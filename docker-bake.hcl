variable "REGISTRY" {
  default = "derechi"
}

variable "SHA" {
  default = "local"
}

function "tags" {
  params = [name]
  result = ["${REGISTRY}/derechi-${name}:latest", "${REGISTRY}/derechi-${name}:${SHA}"]
}

function "gha" {
  params = [name]
  result = ["type=gha,scope=${name}"]
}

group "default" {
  targets = ["postgis", "db-postgres", "client-api", "admin-api", "worker", "notification", "ingest"]
}

target "arm64" {
  platforms = ["linux/arm64"]
}

target "postgis" {
  inherits   = ["arm64"]
  context    = "docker/postgres"
  args       = { POSTGIS_IMAGE = "imresamu/postgis:17-3.5" }
  tags       = tags("postgis")
  cache-from = gha("postgis")
  cache-to   = gha("postgis")
}

target "service" {
  name = item.name
  matrix = {
    item = [
      { name = "db-postgres", module = "DB-Postgres" },
      { name = "client-api", module = "Client-API" },
      { name = "admin-api", module = "Admin-API" },
      { name = "worker", module = "Worker" },
      { name = "notification", module = "Notification" },
    ]
  }
  inherits   = ["arm64"]
  context    = "."
  args       = { MODULE = item.module }
  tags       = tags(item.name)
  cache-from = gha(item.name)
  cache-to   = gha(item.name)
}

target "ingest" {
  inherits   = ["arm64"]
  context    = "."
  target     = "ingest"
  tags       = tags("ingest")
  cache-from = gha("ingest")
  cache-to   = gha("ingest")
}
