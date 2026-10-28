# Логи, стан, відкат та інше.
# `docker compose` сам знає compose-файл машини і env.prod (COMPOSE_FILE і COMPOSE_ENV_FILES
# у ~/.bashrc, див. 1-machine-setup.sh), тож команди однакові на обох машинах і з будь-якої теки.

# derechi-api і derechi-worker: хости з ~/.ssh/config (див. 1-machine-setup.sh)


# ===== Стан =====

# Контейнери і їхній стан
docker compose ps

# Пам'ять і CPU контейнерів
docker stats --no-stream

# Місце на диску
df -h /
docker system df

docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
free -h                     
uptime                      



# ===== Логи =====

# Живі логи всіх сервісів (вихід: Ctrl+C)
docker compose logs -f --tail=200

# Логи одного сервісу
#   машина 1: postgres, db-postgres, postgres-backup, caddy, client-api, admin-api, keycloak, prometheus-agent
#   машина 2: worker, notification, ingest, prometheus-agent
docker compose logs -f --tail=200 client-api

# Логи за останню годину
docker compose logs --since 1h client-api


# ===== Перезапуск і зупинка =====

# Перезапустити один сервіс
docker compose restart client-api

# Зупинити все (дані бази лишаються у volume)
docker compose down


# ===== Відкат на старішу версію =====

# <sha> = коміт, з якого workflow images збирав образи (видно в Actions і в GHCR).
# Діє до наступного оновлення; щоб закріпити, впишіть IMAGE_TAG=<sha> в env.prod.
IMAGE_TAG=<sha> docker compose pull
IMAGE_TAG=<sha> docker compose up -d


# ===== База (машина 1) =====

# Консоль бази
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

# Прогнати міграції ще раз
docker compose up db-postgres

# Чи видно Postgres з машини 2 (виконувати на машині 2; має надрукувати OK)
timeout 3 bash -c '</dev/tcp/10.0.0.10/5432' && echo OK

# Підключитися до бази з ПК (IDEA, pgAdmin, локальний Admin-API з POSTGRES_PORT=15432): тунель на localhost:15432.
# Не 5432: його тримає локальний postgres з docker-compose.yml.
ssh -N -L 15432:10.0.0.10:5432 derechi-api

# pgAdmin з дев-compose (http://localhost:5050, профіль dev): Register → Server руками, не в servers.json
# (він у git і читається лише при першому старті pgAdmin; пароль збережеться в локальному volume pgadmindata).
#   Host:                host.docker.internal   (pgAdmin у контейнері: localhost там — сам контейнер)
#   Port:                15432
#   Maintenance DB:      derechi (або keycloak)
#   Username / Password: POSTGRES_USER / POSTGRES_PASSWORD з env.prod, не дев-pgpass
# Це власник бази з повними правами: схему руками не міняти, її веде лише DB-Postgres.


# ===== Caddy (машина 1) =====

# Перечитати Caddyfile без перезапуску
docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile


# ===== Keycloak (машина 1) =====

# Консоль ззовні закрита (Caddy віддає 404 на /admin і realm master). Тунель з ПК,
# далі http://localhost:8180/admin, логін KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD
ssh -N -L 8180:localhost:8180 derechi-api

# Realm імпортується лише при першому старті: зміни в derechi-realm.json самі не накотяться.
# Бази keycloak немає (volume створений до появи initdb-скрипта)? Створити руками і перезапустити keycloak:
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE keycloak OWNER \"$POSTGRES_USER\";"'
