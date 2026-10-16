# Перший запуск. Спершу машина 1, потім машина 2: Worker ходить у Postgres машини 1.
# На машини клонується репозиторій (compose-файли і конфіги), з ПК копіюється лише env.prod.

# derechi-api і derechi-worker: хости з ~/.ssh/config (див. 1-machine-setup.sh)


# ===== На вашому ПК, до всього =====

# 1. Запушити зміни в main і запустити workflow images на GitHub
#    (Actions → images → Run workflow), дочекатися, поки образи з'являться в GHCR.

# 2. DNS у Vercel: три записи A, імена api, admin і auth, значення = публічна IP машини 1. Перевірити:
nslookup api.derechi.site
nslookup admin.derechi.site
nslookup auth.derechi.site

# 3. У env.prod вписати POSTGRES_PRIVATE_IP=10.0.0.10 (фіксована приватна IP машини 1, див. 0-oracle-cloud.md).

# 4. Скопіювати env.prod на обидві машини (з кореня проєкту)
scp env.prod derechi-api:~/env.prod
scp env.prod derechi-worker:~/env.prod


# ===== Машина 1 =====

ssh derechi-api

git clone https://github.com/Shpytchuk-Vasyl/DeRechi.git ~/DeRechi
mv ~/env.prod ~/DeRechi/env.prod
cd ~/DeRechi

# Якщо env.prod редагувався на Windows: прибрати \r у кінцях рядків
sed -i 's/\r$//' env.prod

# POSTGRES_PRIVATE_IP має бути серед адрес цієї машини
grep POSTGRES_PRIVATE_IP env.prod
ip -4 addr | grep inet

# Скачати образи і запустити
docker compose pull
docker compose up -d

# Стан (db-postgres має бути Exited (0): це міграції, вони завершуються)
docker compose ps

# Міграції пройшли?
docker compose logs db-postgres

# Сертифікат отримано? (шукайте "certificate obtained successfully")
docker compose logs caddy

# API відповідає ззовні?
curl -X POST https://api.derechi.site/graphql -H 'Content-Type: application/json' -d '{"query":"{ countries { code currency } }"}'

# Keycloak імпортував realm? (шукайте "Realm 'derechi' imported"). Перший старт у режимі start довгий:
# Quarkus збирає себе, хвилина-дві.
docker compose logs keycloak | grep -i -E "imported|started in"
curl -s https://auth.derechi.site/realms/derechi/.well-known/openid-configuration | head -c 200; echo
curl -sI https://auth.derechi.site/admin/ | head -1     # має бути 404: консоль ззовні закрита

# Адмінка редіректить на логін Keycloak?
curl -sI https://admin.derechi.site/admin | grep -i location

# Користувачів у realm немає. Консоль Keycloak — лише тунелем (на ПК):
#   ssh -N -L 8180:localhost:8180 derechi-api
# далі http://localhost:8180/admin, логін KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD,
# realm derechi → Users → Add user, Credentials → пароль, Role mapping → ADMIN_SUPER (або EDITOR/VIEWER).

# Бекап: через ~5 хвилин має бути успішне завантаження, а файл — у бакеті B2
docker compose logs postgres-backup


# ===== Машина 2 =====

ssh derechi-worker

git clone https://github.com/Shpytchuk-Vasyl/DeRechi.git ~/DeRechi
mv ~/env.prod ~/DeRechi/env.prod
cd ~/DeRechi
sed -i 's/\r$//' env.prod

# Postgres машини 1 видно? Має надрукувати OK.
# Якщо ні: машина 1 не запущена, або в NSG derechi-api немає правила TCP 5432 з 10.0.1.0/24.
timeout 3 bash -c '</dev/tcp/10.0.0.10/5432' && echo OK

# Скачати образи і запустити
docker compose pull
docker compose up -d
docker compose ps

# Обидва сервіси піднялися і підключилися до CloudAMQP?
docker compose logs --tail=100 worker notification

# Ingest: userbot входить у Telegram один раз, сесія лишається у volume tdlib-data.
# Потрібні TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_PHONE в env.prod; код прийде в Telegram.
# Два TDLib на одній теці сесії не працюють, тому спершу stop.
docker compose stop ingest
docker compose run --rm -it ingest --login
docker compose start ingest
docker compose logs  -f --tail=100 ingest



docker compose exec keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user vasyl_admin --config /tmp/kcadm.config
# it will ask for the password: KEYCLOAK_ADMIN_PASSWORD from env.prod

docker compose exec keycloak /opt/keycloak/bin/kcadm.sh update realms/master \
  -s 'attributes.frontendUrl=http://localhost:8180' --config /tmp/kcadm.config


# On your PC, open the tunnel: 
ssh -N -L 8180:localhost:8180 derechi-api
# Go to http://localhost:8180/admin and log in as vasyl_admin with KEYCLOAK_ADMIN_PASSWORD. This console is the right place for that account.
# In the top-left realm switcher, choose derechi, not master.
# Users → Add user: enter your email (there's no username field, the email becomes the username) and your first and last name → Create.
# Credentials → Set password: turn Temporary off, or Keycloak will ask you to change the password on first login.
# Role mapping → Assign role → Filter by realm roles → ADMIN_SUPER.
# Then log in at https://admin.derechi.site with that email and password.


#Правильно так:

# Вікно 1 (лишити відкритим, поки працюєте з Facebook):


ssh -D 1080 -N derechi-worker
#Команда нічого не виводить і просто «висить», так і має бути.

# Вікно 2:


& "C:\Program Files\Google\Chrome\Application\chrome.exe" --proxy-server="socks5://127.0.0.1:1080" --user-data-dir="$env:TEMP\fb-server"
# Перевірка на https://api.ipify.org має показати 144.24.240.229, адресу воркера. Тоді заходьте у Facebook.