# Повторний запуск: оновлення до нових образів і конфігів.
# Спершу запушіть зміни в main і запустіть workflow images на GitHub
# (Actions → images → Run workflow), дочекайтеся кінця.

# derechi-api і derechi-worker: хости з ~/.ssh/config (див. 1-machine-setup.sh)


# ===== На вашому ПК: лише якщо змінився env.prod =====

scp env.prod derechi-api:~/DeRechi/env.prod
scp env.prod derechi-worker:~/DeRechi/env.prod
scp __deploy/docker-compose.prod-worker.yml derechi-worker:~/DeRechi/__deploy/
scp __deploy/docker-compose.prod-api.yml derechi-api:~/DeRechi/__deploy/



# ===== Машина 1 =====

ssh derechi-api
cd ~/DeRechi

git pull
sed -i 's/\r$//' env.prod
docker compose pull
docker compose up -d --remove-orphans
docker image prune -f


# Міграції нової версії пройшли? (db-postgres запускається знову при кожному up)
docker compose ps
docker stats
docker compose logs db-postgres


# ===== Машина 2 =====

ssh derechi-worker
cd ~/DeRechi

git pull
sed -i 's/\r$//' env.prod
docker compose pull
docker compose up -d --remove-orphans
docker image prune -f
docker compose ps
docker stats
