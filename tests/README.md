# Stack tests

```bash
tests/run.sh                     # integration + load (2 min), report in tests/reports/latest/index.html
tests/run.sh --profile smoke     # quick, what CI runs
tests/run.sh --only integration  # or --only load; --keep leaves the stack up (Grafana :13000)
```

| Path | What |
|---|---|
| `run.sh` | the one command: isolated stack up, tests, report, stack down |
| `docker-compose.yml` | the stack: Client-API, Worker, Notification, PostgreSQL, RabbitMQ, Mailpit, Prometheus, Grafana |
| `integration/` | JUnit `*IT` tests across services |
| `load/` | k6 scripts and the seed |
| `infra/` | migrations image, Prometheus config, report generator |

Details: [`__docs/developer-guide/stack-tests.md`](../__docs/developer-guide/stack-tests.md).
