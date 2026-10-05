# Load tests

k6 load tests for Client-API's GraphQL endpoint. The full guide: profiles, options, test data,
how to read the results: [`__docs/developer-guide/load-testing.md`](../__docs/developer-guide/load-testing.md).

```bash
# stack + Prometheus + Grafana
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# 100 000 notices
docker exec -i derechi-postgres psql -U derechi -d derechi -v ON_ERROR_STOP=1 -v items=50000 < load-tests/seed/seed.sql

# a run: PROFILE=smoke|load|stress|soak
docker compose -f docker-compose.yml -f docker-compose.services.yml run --rm -e PROFILE=load -e RATE=20 k6

# remove everything the seed and the runs created
docker exec -i derechi-postgres psql -U derechi -d derechi -v ON_ERROR_STOP=1 < load-tests/seed/cleanup.sql
```

| Path | What it is |
|---|---|
| `client-api.js` | the test: `browse` and `publish` scenarios |
| `lib/config.js` | profiles, thresholds, environment variables |
| `lib/queries.js` | the operations, copied from `Web-Client/src/graphql/documents.ts` |
| `lib/data.js` | random filters, notices and contacts |
| `lib/graphql.js` | the request helper; counts GraphQL `errors` as failures |
| `seed/seed.sql`, `seed/cleanup.sql` | test data in and out |
