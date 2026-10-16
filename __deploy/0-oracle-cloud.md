# Мережа і машини в Oracle Cloud

Один раз, у консолі Oracle. Потім — `1-machine-setup.sh`.

```
VCN derechi-vcn 10.0.0.0/16
├── public  10.0.0.0/24  ⇄ Internet Gateway
│   └── машина 1 derechi-api    10.0.0.10 + публічна IP
│       Caddy, Gateway, Client-API, Admin-API, Keycloak, Postgres; ззовні 80/443, з машини 2 — 5432
└── private 10.0.1.0/24  → NAT Gateway (інтернет), Service Gateway (сервіси Oracle)
    └── машина 2 derechi-worker 10.0.1.10, без публічної IP
        Worker, Notification; SSH лише з машини 1
```

- Машина 1 приймає трафік з інтернету, тому стоїть у публічній підсіті.
- Машині 2 вхідні з'єднання не потрібні: Worker і Notification лише ходять назовні. Тому вона в
  приватній підсіті й виходить в інтернет через NAT Gateway. Ззовні до неї не достукатися взагалі,
  SSH — лише через машину 1.
- Service Gateway веде трафік до сервісів самого Oracle (Object Storage, моніторинг, агенти) мережею
  Oracle, а не через NAT. Наш стек ним зараз не користується (бекапи йдуть у B2), але він безкоштовний
  і знадобиться, якщо щось переїде в OCI.

## 1. VCN

**Networking → Virtual Cloud Networks → Start VCN Wizard → Create VCN with Internet Connectivity**

| Поле | Значення |
|---|---|
| VCN name | `derechi-vcn` |
| VCN IPv4 CIDR | `10.0.0.0/16` |
| Public subnet CIDR | `10.0.0.0/24` |
| Private subnet CIDR | `10.0.1.0/24` |

Майстер створить усе одразу: Internet Gateway, **NAT Gateway**, **Service Gateway**, обидві підсіті,
таблиці маршрутів і security lists. Після створення перевірте:

- **Route Table for Private Subnet**: `0.0.0.0/0` → NAT Gateway і `All <регіон> Services in Oracle
  Services Network` → Service Gateway.
- **Security List for Private Subnet**: ingress TCP 22 з `10.0.0.0/16` (SSH з машини 1), egress — усе.
- **Default Security List** (публічна підсіть): ingress TCP 22 з `0.0.0.0/0`.

Якщо майстер падає на NAT або Service Gateway з помилкою ліміту: **Governance → Limits, Quotas and
Usage**, сервіс `Networking`, рядки `NAT gateway count` / `Service gateway count`. На Always Free ліміт
буває нульовим; його знімає перехід на Pay As You Go (у межах безкоштовних ресурсів — так само $0).

### Якщо NAT недоступний

Підсіть `10.0.1.0/24` робиться **публічною**, а закривається не маршрутом, а правилами: машина 2
отримує публічну IP лише для виходу назовні, вхідний у неї тільки SSH з VCN. Адреси ті самі, тож
скрипти й `~/.ssh/config` не змінюються.

1. Майстер **Create VCN with Internet Connectivity** уже створив VCN, Internet Gateway і публічну
   підсіть. Якщо він упав, VCN створюється без нього: **Create VCN** (`derechi-vcn`, `10.0.0.0/16`),
   далі в ній **Internet Gateways → Create**, у **Default Route Table** правило `0.0.0.0/0` → цей
   Internet Gateway, **Subnets → Create** `public`, `10.0.0.0/24`, Public Subnet, Default Route Table,
   Default Security List.
2. **Security Lists → Create** `derechi-worker`: ingress TCP 22 з `10.0.0.0/16`, egress усе на
   `0.0.0.0/0`.
3. Якщо майстер створив приватну підсіть `10.0.1.0/24` — видалити її (тип підсіті після створення не
   змінити). **Subnets → Create** `worker`, `10.0.1.0/24`, **Public Subnet**, Default Route Table,
   security list `derechi-worker`.
4. Машина 2 у цій підсіті — **з** публічною IPv4. Без неї з публічної підсіті немає виходу в інтернет.

## 2. NSG для машини 1

`derechi-vcn` → **Network Security Groups → Create Network Security Group**, назва `derechi-api`:

| Direction | Protocol | Port | Source | Навіщо |
|---|---|---|---|---|
| Ingress | TCP | 80 | `0.0.0.0/0` | Caddy, сертифікат Let's Encrypt |
| Ingress | TCP | 443 | `0.0.0.0/0` | API |
| Ingress | UDP | 443 | `0.0.0.0/0` | HTTP/3 |
| Ingress | TCP | 5432 | `10.0.1.0/24` | Postgres для машини 2 |

Egress-правило не потрібне: security list підсіті вже випускає все назовні.

## 3. Машини

**Compute → Instances → Create instance**, двічі. SSH-ключ один на обидві: на першій
«Generate a key pair» і завантажити обидва файли в `~/.ssh/`, на другій «Upload public key» з `.pub`.

| | Машина 1 | Машина 2 |
|---|---|---|
| Name | `derechi-api` | `derechi-worker` |
| Image | Canonical Ubuntu 24.04 Minimal aarch64 | Canonical Ubuntu 24.04 Minimal aarch64 |
| Shape | Ampere `VM.Standard.A1.Flex` | Ampere `VM.Standard.A1.Flex` |
| OCPU / RAM | 3 / 6 GB | 1 / 6 GB |
| Subnet | public `10.0.0.0/24` | **private** `10.0.1.0/24` |
| Public IPv4 | так | ні (у приватній підсіті й не дасть) |
| Private IPv4 (Show advanced) | `10.0.0.10` | `10.0.1.10` |
| NSG | `derechi-api` | — |
| Boot volume | 50–60 GB | за замовчуванням (46.6 GB) |

Машині 1 вистачає 6 GB: сума лімітів її контейнерів у найгіршому випадку ~4.3 GB (див. `*_MEMORY` у
`docker-compose.prod-api.yml`), а реально зайнято значно менше. RAM у A1.Flex міняється без
перестворення: **Edit → Shape**, машина перезапуститься.

Ліміт Always Free — 4 OCPU / 24 GB і 200 GB дисків на все разом. Менше 46.6 GB boot volume не буває; збільшити
потім можна без перестворення машини, зменшити — ніколи. Реально зайнято ~10–15 GB (образи, swap, база).

Фіксовані приватні IP прописані у скриптах: `POSTGRES_PRIVATE_IP=10.0.0.10` в `env.prod`,
`HostName 10.0.1.10` у `~/.ssh/config`. Якщо взяли інші — поміняйте там.

**Out of capacity for shape VM.Standard.A1.Flex** — інший Availability Domain у Placement, пізніше,
або Pay As You Go.

## 4. Публічна IP машини 1

Ефемерна IP переживає перезавантаження й зупинку, зникає лише з видаленням машини. Щоб DNS-запис
`api` пережив і перестворення: **Networking → IP Management → Reserved Public IPs** і призначити її
машині 1 замість ефемерної.

## 5. Перевірка

```bash
ssh derechi-api                        # машина 1 напряму
ssh derechi-worker                     # машина 2 через машину 1
curl -sI https://github.com | head -1  # на машині 2: вихід через NAT працює
```
