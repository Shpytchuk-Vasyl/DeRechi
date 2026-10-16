# Налаштування машини. Один раз на кожній з двох машин.
# Копіюйте команди по кроку в SSH-консоль.
# Мережа і самі машини створюються в консолі Oracle: див. 0-oracle-cloud.md.


# ===== На вашому ПК, один раз =====

# Машина 2 у приватній підсіті й публічної IP не має: на неї заходять через машину 1.
# ssh -J не передає ключ (-i) на проміжну машину, тому обидві машини описуються в ~/.ssh/config
# (Git Bash і PowerShell читають той самий файл, C:\Users\<ви>\.ssh\config). Допишіть у нього:
#
#   Host derechi-api
#       HostName <публічна ip машини 1>
#       User ubuntu
#       IdentityFile ~/.ssh/<файл ключа>
#
#   Host derechi-worker
#       HostName 10.0.1.10
#       User ubuntu
#       IdentityFile ~/.ssh/<файл ключа>
#       ProxyJump derechi-api
#
# Далі всюди ssh/scp на derechi-api і derechi-worker, без ключа й IP.


# ===== На вашому ПК =====

# Зайти на машину
ssh derechi-api       # машина 1
ssh derechi-worker    # машина 2


# ===== На машині (обидві) =====

# Оновити пакети
sudo apt-get update && sudo apt-get -y upgrade

# curl і git (в образі Minimal їх може не бути)
sudo apt-get install -y curl git

# Поставити Docker
curl -fsSL https://get.docker.com | sudo sh

# Дозволити docker без sudo
sudo usermod -aG docker ubuntu

# Обмежити логи контейнерів (інакше ростуть безмежно і забивають диск)
echo '{"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker

# Swap 2 GB
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Щоб писати просто `docker compose ...`, без --env-file і -f: Compose бере їх зі змінних.
# Репозиторій клонується в ~/DeRechi у 2-first-run.sh; до того змінні просто ні на що не вказують.
echo 'export COMPOSE_FILE=$HOME/DeRechi/__deploy/docker-compose.prod-api.yml COMPOSE_ENV_FILES=$HOME/DeRechi/env.prod' >> ~/.bashrc       # машина 1
echo 'export COMPOSE_FILE=$HOME/DeRechi/__deploy/docker-compose.prod-worker.yml COMPOSE_ENV_FILES=$HOME/DeRechi/env.prod' >> ~/.bashrc    # машина 2


# ===== На машині (обидві) =====

# Вийти і зайти знову, щоб група docker і змінні Compose запрацювали
exit
ssh derechi-api       # або derechi-worker

# Перевірити: має відпрацювати без sudo
docker compose version
