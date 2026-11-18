# Прод на Oracle Cloud: усе в одному місці

Зведено з `0-oracle-cloud.md` … `4-other.sh` і з сесій, у яких це налаштовувалося (жовтень 2026).
Порядок розділів — порядок дій при повторному налаштуванні з нуля.

## Що де живе

```
Vercel (derechi.site, Web-Client)
   │ HTTPS
   ▼
Oracle VCN derechi-vcn 10.0.0.0/16, Home Region, Ubuntu 24.04 Minimal aarch64
├── public  10.0.0.0/24 ⇄ Internet Gateway
│   └── машина 1 derechi-api    10.0.0.10 + публічна IP (зараз 140.238.214.133)
│       caddy → client-api, admin-api, keycloak; postgres, db-postgres (разово),
│       postgres-backup, prometheus-agent;  ззовні 80/443, з машини 2 — 5432
└── private 10.0.1.0/24 → NAT Gateway, Service Gateway
    └── машина 2 derechi-worker 10.0.1.10, без публічної IP
        worker, notification, ingest, prometheus-agent;  SSH лише через машину 1
```

| Що | Де | Чим замінено локальне |
|---|---|---|
| RabbitMQ | CloudAMQP, Little Lemur (free), TLS 5671 | `rabbitmq` |
| Файли | Backblaze B2, публічний бакет `derechi-files` | MinIO (його CE закинутий, у прод не брати) |
| Бекапи бази | B2, окремий приватний бакет, контейнер `nfrastack/db-backup` | — |
| Пошта | Brevo SMTP `smtp-relay.brevo.com:587` (25 в Oracle закритий) | Mailpit |
| Метрики | Grafana Cloud Free, `prometheus-agent` (`--agent`) на кожній машині | Prometheus + Grafana |
| Образи | GHCR `ghcr.io/shpytchuk-vasyl/derechi-*`, workflow `images` (arm64) | локальна збірка |
| DNS | Vercel: `A api/admin/auth` → публічна IP машини 1 | — |

`env.prod` — один файл на обидві машини, у git його немає. **Збережи його копію поза машинами**
(менеджер паролів), разом з `BACKUP_ENCRYPT_PASSPHRASE`: без нього жоден бекап не розшифрується.

---

## 1. Акаунти й зовнішні сервіси (один раз)

### Oracle Cloud — реєстрація

- Справжня фізична картка; billing address і країна збігаються з карткою; **VPN вимкнути**.
  Один безкоштовний акаунт на людину (картка, телефон, пошта). Багато спроб поспіль — тимчасовий бан.
- Customer type: **Individual**. Cloud Account Name (tenancy) потім **не змінити**.
- **Home Region не змінити**, і A1 безкоштовні лише в ньому. Під нього підбирається решта:
  регіон CloudAMQP і Vercel Function Region (Frankfurt → `fra1`, Stockholm → `arn1`, Paris/Marseille → `cdg1`).
- Поки триває trial ($300 / 30 днів), створювати лише те, що позначено **Always Free-eligible**:
  інше після trial зупинять.
- MFA увімкнути одразу.

**Ліміт Always Free (офіційна сторінка, жовтень 2026):** Ampere A1 — **2 OCPU / 12 GB на все разом**
(1 500 OCPU-год і 9 000 GB-год на місяць; раніше було 4 / 24), 200 GB дисків, 2 VCN, 10 TB трафіку.
Простоюючі машини Oracle може забрати: за 7 днів CPU, мережа і пам'ять (лише A1) нижче 20 %
(95-й перцентиль). На Pay As You Go цього правила немає.

**Pay As You Go** дає A1 у пріоритеті й знімає нульові ліміти NAT. Коштує $0 у межах Always Free,
але при переході картку **блокують на ~$93–100** на кілька днів (hold, не списання). Після переходу
одразу: **Billing → Budgets → Create Budget**, $1, сповіщення на пошту.

### CloudAMQP

1. customer.cloudamqp.com → Create New Instance → **Little Lemur (Free)**, регіон поруч з Oracle.
2. Details → AMQP URL `amqps://USER:PASS@HOST/VHOST` → в `env.prod`:
   `RABBITMQ_HOST`, `RABBITMQ_PORT=5671`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`,
   `RABBITMQ_VHOST` (= USER), `RABBITMQ_SSL=true`.
3. Черги: «Import definitions» на free-тарифі **немає**, тому скриптом з кореня проєкту:
   ```bash
   python docker/rabbitmq/import.py env.prod          # якщо падає на quorum: додати --classic
   ```
   Має бути 5 exchange (`notice.status.exchange`, `notice.action.exchange`, `claims.exchange`,
   `notification.exchange`, `dead-letter.exchange`) і 6 черг (`worker|notification|ingest` `.queue` + `.dlq`).
   Скрипт лише додає й оновлює: прибрані exchange, черги й біндинги видаляються руками в UI.
4. **Alarms** (замість Prometheus-алерту `DeadLetters`): Queue alarm — Value threshold `0`,
   Time threshold `60`, Queue regexp `.*\.dlq$`, Message type `Ready`, Reminder `86400`; отримувач —
   своя пошта. Consumer alarm (замінює частину `TargetDown`): regexp
   `^(worker|notification|ingest)\.queue$`, менше 1 споживача 60 с.
5. **Не запускати локально Worker, Notification чи Ingest з прод-брокером**: вони заберуть
   прод-повідомлення. Admin-API можна, він лише шле.

### Backblaze B2

Регіон акаунта фіксується при реєстрації (у нас `eu-central-003`).

**Бакет файлів** `derechi-files`:
- Files in Bucket: **Public**. **Object Lock не вмикати**: вимкнути його потім не можна, лише
  перестворити бакет. SSE-B2 можна, SSE-C ні.
- Lifecycle: **Keep only the last version** (інакше видалені картинки займають 10 GB).
- Application Key `derechi-app`: лише цей бакет, Read and Write → `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`.
- CORS — **лише через CLI** (UI не вміє дозволити `s3_put`) і **з Git Bash**: PowerShell 5.1 з'їдає
  лапки в JSON. `pip install b2`; якщо `b2` не знаходиться, він лежить у
  `%LOCALAPPDATA%\Packages\PythonSoftwareFoundation.Python.3.13_*\LocalCache\local-packages\Python313\Scripts\b2.exe`.
  ```bash
  b2 account authorize        # master key, не derechi-app
  b2 bucket update --cors-rules '[{"corsRuleName":"web-uploads","allowedOrigins":["https://derechi.site"],"allowedOperations":["s3_put","s3_get","s3_head"],"allowedHeaders":["*"],"exposeHeaders":["ETag"],"maxAgeSeconds":3600}]' derechi-files allPublic
  b2 bucket get derechi-files # у виводі corsRules з s3_put
  ```
  Після цього в UI не натискати **Update CORS Rules** — перезапише. Повторний запуск команди
  замінює список origins повністю.
- `env.prod`: `MINIO_ENDPOINT` і `MINIO_URI` = `https://s3.eu-central-003.backblazeb2.com`,
  `MINIO_REGION=eu-central-003`, `MINIO_BUCKET`, `MINIO_PUBLIC_URL=https://api.derechi.site/files`
  (не localhost — інакше в адмінці биті фото).
- Якщо Admin-API падає з помилкою про checksum: `AWS_REQUEST_CHECKSUM_CALCULATION=WHEN_REQUIRED`.

**Бакет бекапів**: Private, без Object Lock, Lifecycle custom — Days till hide `30`, Days till delete `1`.
Ключ лише на цей бакет (спершу Write Only; якщо в логах `AccessDenied` — Read and Write).
`env.prod`: `BACKUP_S3_HOST` **без `https://`** (протокол уже в compose), `BACKUP_S3_REGION`,
`BACKUP_S3_BUCKET`, `BACKUP_S3_KEY_ID`, `BACKUP_S3_KEY_SECRET`, `BACKUP_ENCRYPT_PASSPHRASE`,
`BACKUP_INTERVAL=1440` (хвилини; 4320 = раз на 3 дні).

### Brevo

Домен підтвердити (DKIM/DMARC — записи у Vercel DNS). `NOTIFY_EMAIL_HOST=smtp-relay.brevo.com`,
`NOTIFY_EMAIL_PORT=587`, логін і SMTP-ключ з кабінету, `NOTIFY_EMAIL_FROM=no-reply@derechi.site`.

### Grafana Cloud

- grafana.com → My Account (Cloud Portal) → stack → плитка **Prometheus → Details**:
  Remote Write Endpoint → `GRAFANA_CLOUD_PROM_URL`, Username (число) → `GRAFANA_CLOUD_PROM_USER`,
  **Generate now** → `GRAFANA_CLOUD_TOKEN` (показується один раз; право `metrics:write`).
- У логах агента `401` — не той user/token, `404` — не той URL. Перевірка: Explore → `up{job="derechi-services"}`.
- Дашборд **4701** імпортувати лише через **Dashboards → New → Import** (ввести `4701` або Upload JSON)
  і на тому екрані обрати джерело `grafanacloud-<stack>-prom`. Вставлений як JSON Model, він лишає
  `${DS_PROMETHEUS}` і порожні панелі. Полагодити такий: Settings → Variables → New, Type
  **Data source**, Name **`DS_PROMETHEUS`**, тип Prometheus.
- Free tier — 10k активних серій; у нас ~460 після `metric_relabel_configs` в агентах. Скрейп раз на 60 с:
  з більшим інтервалом `rate(...[1m])` і `[2m]` дають порожнечу.

### Образи (GHCR)

GitHub → Actions → **images** → Run workflow (лише вручну, з `main`), ~10 хв. Збирає на
`ubuntu-24.04-arm`: машини Oracle ARM, тому x86-образи на них падають з `exec format error`.
Пакети мають бути **Public** (github.com/Shpytchuk-Vasyl?tab=packages), інакше на машинах
`docker login ghcr.io` з classic PAT `read:packages`. Новий пакет (як `derechi-ingest`) GitHub створює
приватним: після першого прогону відкрити його Package settings → Change visibility → Public.

### Інше

- **Fourthwall**: вебхук `ORDER_PLACED` на `https://api.derechi.site/api/client/webhooks/fourthwall`,
  секрет → `FOURTHWALL_WEBHOOK_SECRET` в `env.prod` (не у Vercel).
- **Google Maps**: ключ у репозиторії публічний — обмежити за referrer `https://derechi.site/*` і
  `https://admin.derechi.site/*`.
- **Keycloak**: у `env.prod` поміняти `KEYCLOAK_ADMIN_PASSWORD` і `KEYCLOAK_SERVICES_SECRET` з дев-значень.

---

## 2. Oracle: мережа і машини

Регіон угорі праворуч — Home Region, Compartment — root.

### VCN

**Networking → Virtual cloud networks → Actions → Start VCN Wizard → Create VCN with Internet Connectivity**:
`derechi-vcn`, VCN `10.0.0.0/16`, public `10.0.0.0/24`, private `10.0.1.0/24`. Майстер створює
Internet/NAT/Service Gateway, маршрути й security lists. Перевірити:
- Route Table for Private Subnet: `0.0.0.0/0` → NAT Gateway, `All <регіон> Services` → Service Gateway;
- Security List for Private Subnet: ingress TCP 22 з `10.0.0.0/16`;
- Default Security List: ingress TCP 22 з `0.0.0.0/0`.

Якщо майстер падає на NAT/Service Gateway (ліміт 0 на Always Free): або Pay As You Go, або запасний
варіант — підсіть `10.0.1.0/24` робиться **публічною** з окремим security list `derechi-worker`
(ingress лише TCP 22 з `10.0.0.0/16`, egress усе), машина 2 тоді **з** публічною IP. Адреси ті самі,
скрипти й `~/.ssh/config` не міняються. Тип підсіті після створення не змінити — лише перестворити.

### NSG `derechi-api` (для машини 1)

`derechi-vcn` → Network Security Groups → Create. Кожне правило: Stateless **без** галочки, Direction
Ingress, Source Type CIDR, Source Port Range **порожній**, і **не лишати `All Protocols`** (відкриє все):

| Source | Protocol | Dest. port | Навіщо |
|---|---|---|---|
| `0.0.0.0/0` | TCP | 80 | Let's Encrypt, редірект |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| `0.0.0.0/0` | UDP | 443 | HTTP/3 |
| `10.0.1.0/24` | TCP | 5432 | Postgres для машини 2. **Ніколи не `0.0.0.0/0`** |

Egress не треба.

### Машини

**Compute → Instances → Create instance.** Спершу обрати Shape, потім Image — тоді список одразу ARM.

| Поле | `derechi-api` | `derechi-worker` |
|---|---|---|
| Image | Canonical Ubuntu 24.04 **Minimal aarch64** | те саме |
| Shape | `VM.Standard.A1.Flex` | `VM.Standard.A1.Flex` |
| OCPU / RAM | **1 / 6 GB** (див. нижче) | 1 / 6 GB |
| Security (Shielded, Confidential, ZPR attributes) | нічого не вмикати | нічого |
| VNIC name | `derechi-api` | `derechi-worker` |
| Subnet | public | **private** |
| Private IPv4 | Manually → `10.0.0.10` | Manually → `10.0.1.10` |
| Public IPv4 | **так** | ні |
| Advanced → Use network security groups | **так**, `derechi-api` | ні |
| DNS record | Assign, hostname `derechi-api` | `derechi-worker` |
| Launch options | Let Oracle choose | те саме |
| SSH key | Generate → **завантажити обидва** → `~/.ssh/derechi-oracle.key(.pub)` | Upload `.pub` той самий |
| Boot volume | 46.6 GB за замовчуванням або 50–60 | за замовчуванням |
| Encryption | in-transit як є, свій ключ не обирати | те саме |

- **Shape у формі за замовчуванням 1 / 6** — при іншому розмірі його легко пропустити.
- **Розмір під поточний ліміт 2 / 12 — по 1 OCPU / 6 GB.** Машині 1 6 GB вистачає: найгірший випадок
  лімітів її контейнерів ~4.8 GB (postgres 2g, client-api 768m, admin-api/keycloak/db-postgres
  по 512m, backup 256m, caddy і агент по 128m), реально зайнято ~1.3 GB. Postgres має 2g, бо кожне
  з'єднання, що шукало, тримає hunspell-словники (~96 MB). RAM і OCPU A1.Flex міняються
  без перестворення: Edit → Shape, машина перезапуститься.
- Shielded instance не вмикати: після нього shape не змінити.
- Boot volume менше 46.6 GB не буває; збільшити можна потім, зменшити ніколи. Реально зайнято 10–15 GB.
- Публічна IP переживає перезавантаження, але не перестворення. Щоб DNS пережив і його:
  **Networking → IP Management → Reserved Public IPs** і призначити машині 1.

### «Out of capacity for shape VM.Standard.A1.Flex»

Найчастіша проблема. У порядку надійності:
1. На помилці погодитися на **Save as stack** → ☰ Developer Services → **Resource Manager → Stacks** →
   **Apply**; повторювати Apply, доки job не стане Succeeded. Так машина 1 і створилася.
   Після успіху — **Delete stack** (не Destroy: той видаляє машину) і більше не Apply.
2. Інший Availability Domain (у європейських регіонах часто лише AD-1).
3. Менший shape зараз, збільшити потім.
4. Pay As You Go (hold ~$93).

Інші shapes (E2.1.Micro — 1/8 OCPU і 1 GB, x86; E4/E5/A2 — платні) не підходять.

---

## 3. ПК: SSH

`C:\Users\vasil\.ssh\config` (його ж читають Git Bash і PowerShell):

```
Host derechi-api
    HostName 140.238.214.133
    User ubuntu
    IdentityFile ~/.ssh/derechi-oracle.key
    IdentitiesOnly yes

Host derechi-worker
    HostName 10.0.1.10
    User ubuntu
    IdentityFile ~/.ssh/derechi-oracle.key
    IdentitiesOnly yes
    ProxyJump derechi-api
```

- `ssh -J` не передає ключ на проміжну машину, тому саме `ProxyJump` у конфігу.
- Якщо Git Bash ігнорує конфіг — у файлі BOM на початку: `sed -i '1s/^\xEF\xBB\xBF//' ~/.ssh/config`.
- Перевірка: `ssh derechi-api`, `ssh derechi-worker`, на машині 2 `curl -sI https://github.com | head -1` (вихід через NAT).

---

## 4. Налаштування машини (обидві)

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y curl git                 # у Minimal їх може не бути

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu

# Ліміт логів контейнерів. tee друкує JSON на екран — це не помилка.
echo '{"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker

# Swap 2 GB (в образах Oracle його немає)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Щоб писати просто `docker compose ...` з будь-якої теки
echo 'export COMPOSE_FILE=$HOME/DeRechi/__deploy/docker-compose.prod-api.yml COMPOSE_ENV_FILES=$HOME/DeRechi/env.prod' >> ~/.bashrc      # машина 1
echo 'export COMPOSE_FILE=$HOME/DeRechi/__deploy/docker-compose.prod-worker.yml COMPOSE_ENV_FILES=$HOME/DeRechi/env.prod' >> ~/.bashrc   # машина 2

exit    # перезайти, щоб запрацювали група docker і змінні
docker compose version
```

iptables образу Oracle опубліковані Docker-ом порти не блокує (трафік іде через `FORWARD`), тому
`netfilter-persistent` не потрібен. Лише якщо NSG точно правильна, а 80/443 не відповідають:
```bash
sudo apt-get install -y iptables-persistent
sudo iptables -I INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT
sudo netfilter-persistent save
```

---

## 5. Перший запуск

### На ПК

1. Workflow **images** відпрацював, пакети в GHCR є.
2. Vercel → Domains → `derechi.site` → DNS: `A` для `api`, `admin`, `auth` → публічна IP машини 1
   (старий CNAME `api` на Vercel, якщо є, видалити). **До** старту Caddy, інакше Let's Encrypt
   відмовлятиме й обмежить спроби. Перевірка через публічний DNS:
   ```bash
   nslookup api.derechi.site 1.1.1.1      # на машині nslookup немає: getent hosts api.derechi.site
   ```
3. В `env.prod` `POSTGRES_PRIVATE_IP=10.0.0.10`.
4. Копіювання. На машини клонується `main`, а `__deploy/` є лише в гілці `deploy`, тож compose-файли
   разом з `env.prod` їдуть scp-ом (compose — після `git clone` і `mkdir -p ~/DeRechi/__deploy`):
   ```bash
   scp env.prod derechi-api:~/env.prod
   scp env.prod derechi-worker:~/env.prod
   scp __deploy/docker-compose.prod-api.yml derechi-api:~/DeRechi/__deploy/
   scp __deploy/docker-compose.prod-worker.yml derechi-worker:~/DeRechi/__deploy/
   ```

### Машина 1

```bash
ssh derechi-api
git clone https://github.com/Shpytchuk-Vasyl/DeRechi.git ~/DeRechi
mv ~/env.prod ~/DeRechi/env.prod && cd ~/DeRechi
sed -i 's/\r$//' env.prod                       # якщо редагувався на Windows
ip -4 addr | grep 10.0.0.10                     # POSTGRES_PRIVATE_IP має бути адресою цієї машини
docker compose pull && docker compose up -d
docker compose ps                               # db-postgres — Exited (0): міграції, так і має бути
docker compose logs db-postgres
docker compose logs caddy                       # "certificate obtained successfully"
docker compose logs keycloak | grep -i -E "imported|started in"   # перший старт 1–2 хв
docker compose logs postgres-backup             # за ~5 хв файл у бакеті B2, тека postgres/
```
Якщо бази `keycloak` немає (volume старший за initdb-скрипт):
```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE keycloak OWNER \"$POSTGRES_USER\";"'
docker compose restart keycloak
```
Користувачів у realm немає. Консоль — тунелем з ПК: `ssh -N -L 8180:localhost:8180 derechi-api`, далі
`http://localhost:8180/admin` → realm `derechi` → Users → Add user → Credentials → Role mapping
`ADMIN_SUPER` / `ADMIN_EDITOR` / `ADMIN_VIEWER`.

### Машина 2

```bash
ssh derechi-worker
git clone https://github.com/Shpytchuk-Vasyl/DeRechi.git ~/DeRechi
mv ~/env.prod ~/DeRechi/env.prod && cd ~/DeRechi && sed -i 's/\r$//' env.prod
timeout 3 bash -c '</dev/tcp/10.0.0.10/5432' && echo OK   # ні — машина 1 не запущена або NSG без 5432
docker compose pull && docker compose up -d
docker compose logs --tail=100 worker notification        # Started ...Application, без помилок AMQP
```

**Ingest.** Userbot (окремий Telegram-акаунт) входить один раз, сесія лишається у volume `tdlib-data`.
Потрібні `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` (my.telegram.org), `TELEGRAM_PHONE`; без `TELEGRAM_API_ID`
сервіс стартує й нічого не читає. Два TDLib на одній теці сесії не працюють, тому спершу `stop`:
```bash
docker compose stop ingest
docker compose run --rm -it ingest --login                # код прийде в Telegram, далі пароль 2FA
docker compose start ingest && docker compose logs --tail=100 ingest
```
Той самий акаунт не запускати одночасно локально: обидва інстанси публікуватимуть ті самі пости.
Бот (`TELEGRAM_BOT_TOKEN`) має бути адміном каналу `TELEGRAM_PUBLISH_CHAT` з правом публікації і
видалення: з `channel` в `INGEST_PUBLISHERS` Ingest слухає `ingest.queue` (CloudAMQP), постить у канал
і оголошення, створені на сайті й в адмінці, а при архівації чи видаленні прибирає пост.
Алерти `IngestUnauthorized`/`IngestStalled` з `docker/prometheus/alerts.yml` агент у Grafana Cloud не
везе: за потреби їх створюють там як alert rules на `derechi_ingest_telegram_authorized` і
`derechi_ingest_last_received_seconds`.

### Перевірка з ПК

```bash
curl -X POST https://api.derechi.site/graphql -H 'Content-Type: application/json' -d '{"query":"{ countries { code currency } }"}'
curl -sI https://auth.derechi.site/admin/ | head -1                   # 404: консоль ззовні закрита
curl -sI https://admin.derechi.site/admin | grep -i location          # редірект на Keycloak
```
Якщо з ПК TLS-помилка, а з `1.1.1.1` DNS уже правильний — у Windows закешована стара адреса Vercel:
`ipconfig /flushdns`, або перевірити в обхід кешу
`curl --resolve api.derechi.site:443:140.238.214.133 https://api.derechi.site/...`.
GraphiQL у проді вимкнений, `/graphiql` дає 404 — це нормально.

Повний ланцюжок: оголошення з фото на сайті → фото в B2 → Worker знайшов пару → заявка → лист через Brevo.
CloudAMQP → Connections: client-api, admin-api, worker, notification, ingest. Grafana Cloud: `up{job="derechi-services"}`.

---

## 6. Vercel (Web-Client)

- Import репозиторію, **Root Directory `Web-Client`**. Якщо `pnpm install` падає на версії —
  `ENABLE_EXPERIMENTAL_COREPACK=1`.
- Settings → Environment Variables → **Import .env** з `Web-Client/.env.prod`. `NODE_ENV` і `VERCEL`
  не додавати. Зміна `NEXT_PUBLIC_*` потребує **Redeploy**.
- Регіон функцій: **Build and Deployment → Function Region** (або пошук `F` → `region`); якщо немає —
  `Web-Client/vercel.json` `{"regions": ["fra1"]}`. Перевірка: Deployments → Functions, не `iad1`.
- Domains: `derechi.site` + `www` **редіректом** на `derechi.site` — CORS бакета B2 пускає лише
  `https://derechi.site`, з `www` завантаження фото впаде на CORS.
- Hobby — «non-commercial only», а в нас оплата через Fourthwall: ризик призупинення. Ліміт
  оптимізації картинок 5 000/міс, логи живуть годину.

---

## 7. Оновлення

```bash
# ПК: push у main → Actions → images → Run workflow, дочекатися.
# ПК, гілка deploy: git merge main, щоб __deploy/ і docker/ відповідали тому, що деплоїться.
# ПК, лише якщо змінився docker/rabbitmq/definitions.json — ДО оновлення машин
# (сервіси топологію не оголошують, а публікація в неіснуючий exchange губиться):
python docker/rabbitmq/import.py env.prod
# ПК, лише якщо змінився env.prod або __deploy/*.yml:
scp env.prod derechi-api:~/DeRechi/env.prod
scp env.prod derechi-worker:~/DeRechi/env.prod
scp __deploy/docker-compose.prod-api.yml derechi-api:~/DeRechi/__deploy/
scp __deploy/docker-compose.prod-worker.yml derechi-worker:~/DeRechi/__deploy/

# Кожна машина (спершу 1, потім 2):
cd ~/DeRechi && git pull && sed -i 's/\r$//' env.prod
docker compose pull && docker compose up -d --remove-orphans && docker image prune -f
docker compose ps        # на машині 1 ще: docker compose logs db-postgres
```
`--remove-orphans` прибирає контейнери сервісів, яких у compose більше немає (так пішов `getaway`).
Якщо compose колись копіювався scp-ом, `git pull` зупиниться на невідстежуваних файлах
(`untracked working tree files would be overwritten`): їх видаляють, див. `3-update.sh`.

**Стара топологія RabbitMQ.** Після переходу на `*.queue` (жовтень 2026) у CloudAMQP лишаються
`derechi.items`, `derechi.items.dlx`, `derechi.notifications`, `derechi.notifications.dlx` і черги
`worker.items|claims|archive`, `notification.events` з їхніми `.dlq`. Коли обидві машини оновлені, а ці
черги порожні (Queues → Ready = 0), їх видаляють в UI: Queues → черга → Delete, потім Exchanges.
Повідомлення зі старих DLQ на нову топологію не переграються: routing key змінились (`item.*` → `notice.*`/`claim.*`).

Відкат: `IMAGE_TAG=<sha> docker compose pull && IMAGE_TAG=<sha> docker compose up -d`
(закріпити — `IMAGE_TAG=<sha>` в `env.prod`).

---

## 8. Щоденне

```bash
docker compose ps
docker compose logs -f --tail=200 client-api        # машина 1: postgres db-postgres postgres-backup caddy
                                                    #   client-api admin-api keycloak prometheus-agent
                                                    # машина 2: worker notification ingest prometheus-agent
docker compose logs --since 1h client-api
docker compose restart client-api

free -h                                             # дивитись "available"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
docker inspect -f '{{.Name}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -aq)
sudo dmesg -T | grep -i -E "out of memory|killed process"
df -h / && docker system df
```
Ліміти пам'яті й JVM — `<СЕРВІС>_MEMORY` / `<СЕРВІС>_JAVA_OPTS` в `env.prod`, без правок compose.
Контейнер, що довго тримається вище 90 % ліміту, — кандидат на OOM.

База (машина 1):
```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
ssh -N -L 15432:10.0.0.10:5432 derechi-api          # з ПК: прод-база на localhost:15432 (pgAdmin — 4-other.sh)
```

Відновлення бекапу (перевірено 2026-11-18 на бекапі від 17.11). Файл у B2 — не `pg_dump`, а SQL
власного формату `nfrastack/db-backup`, і просто `psql` на ньому не спрацює:
- **немає словників пошуку** `ukrainian` і `polish` (міграції `001`, `010`). Таблиці відновлюються без
  помилок, але пошук Client-API падає на `CAST(... AS regconfig)`;
- **роль з `!` в імені** дампер пише в `GRANT` як `"""роль"""`, і відновлення падає на цьому рядку;
- **бази `keycloak` у бекапі немає** (знімається лише `derechi`), фото лежать в окремому бакеті B2.

Відновлювати в образ з hunspell (`derechi/postgis:17-3.5-uk` локально, `derechi-postgis` з ghcr на проді),
з кореня репозиторію:
```bash
# 1. Файл з B2 (тека postgres/, *.sql.zst.age) у поточну теку; пароль — BACKUP_ENCRYPT_PASSPHRASE
docker run --rm -it -v "$PWD:/work" -w /work alpine:3.20 sh -c \
  "apk add --no-cache -q age zstd && age -d <файл>.sql.zst.age | zstd -d -o dump.sql"

# 2. Битий GRANT і власник → користувач, під яким відновлюємо
sed -i -E -e 's/ TO """[^"]+""";$/ TO CURRENT_USER;/' \
          -e '/pg_database_owner/!s/OWNER TO "[^"]+"/OWNER TO CURRENT_USER/' dump.sql

# 3. Порожня база й дамп однією транзакцією: при помилці база лишається порожньою
docker exec derechi-postgres sh -c 'createdb -U "$POSTGRES_USER" derechi_restore'
docker exec -i derechi-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d derechi_restore -q -v ON_ERROR_STOP=1 --single-transaction' < dump.sql > /dev/null

# 4. Словники пошуку — з тих самих міграцій
cat DB-Postgres/changelog/changes/001-extentions-and-configuration.sql \
    DB-Postgres/changelog/changes/010-polish-fts.postgresql.sql |
  docker exec -i derechi-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d derechi_restore -q -v ON_ERROR_STOP=1 --single-transaction'

# 5. Перевірка
docker exec -it derechi-postgres sh -c 'psql -U "$POSTGRES_USER" -d derechi_restore'
#   select count(*) from lost_item;                       -- як на проді
#   select count(*) from databasechangelog;               -- = кількість changeset у DB-Postgres/changelog
#   select to_tsvector('ukrainian', 'загубила гаманці');  -- 'гаманець':2 'загубити':1
```
На проді замість `derechi_restore` — `derechi`, без `createdb`. Якщо `db-postgres` уже встиг накотити
міграції, дамп однаково ляже (він робить `DROP TABLE IF EXISTS ... CASCADE`), а словники вже є: крок 4
впаде з `already exists`, його пропускають. Сервіси на відновлену локальну базу — `POSTGRES_DB=derechi_restore`.
На Windows крок 1 запускати з PowerShell (`-v "${PWD}:/work"`), кроки 2–5 — з Git Bash. Розшифрований
`dump.sql` містить контакти людей: після перевірки його видаляють.

Caddy без рестарту: `docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile`. Тека
`docker/caddy` змонтована цілком (`Caddyfile` імпортує `api.Caddyfile`), тож після `git pull` reload бачить
нові файли; зміна змінних оточення Caddy — лише через `docker compose up -d`.

---

## 9. Що варто перевірити, перш ніж налаштовувати знову

- **Ліміт A1.** Машини створювались як 2 / 12 (api) + 1 / 6 (worker) = 3 OCPU / 18 GB, а офіційний
  Always Free зараз 2 / 12. Якщо це вийшло за рахунок trial-кредитів, після trial надлишок можуть
  зупинити. Глянути **Governance → Limits** і **Billing → Cost analysis**; привести до 1/6 + 1/6.
- **`__deploy/` є лише в гілці `deploy`** (локальній): перед деплоєм у неї зливається `main`. На машини
  йде clone `main`, тож compose-файли туди доїжджають лише scp-ом.
- Невдалий бекап ніхто не помітить (алертів немає) — зрідка дивитись, чи свіжий останній файл у бакеті.
- **Бекап не знімає базу `keycloak`**: акаунти адмінів після аварії довелося б створювати заново. Додати
  її другою базою в `postgres-backup` (`DB02_*`) і заодно перевірити, чи нова версія образу дампить словники
  пошуку (див. «Відновлення бекапу» в розділі 8).
- Зовнішнього uptime-моніторингу немає: падіння машини 1 видно лише в Grafana Cloud.
