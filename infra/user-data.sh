#!/bin/bash
# Першы запуск сэрвэра BMUZ (Amazon Linux 2023): Docker, swap і сэрвіс bmuz.
set -euo pipefail

dnf install -y docker
systemctl enable --now docker

if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

mkdir -p /opt/bmuz

cat > /opt/bmuz/env.conf <<'EOF'
REGION=eu-north-1
REGISTRY=899012776854.dkr.ecr.eu-north-1.amazonaws.com
IMAGE=899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmuz:0efb5df
DB_HOST=bmuz.cbas4a8c0z88.eu-north-1.rds.amazonaws.com
SECRET_ID=bmuz/db-app
COVERS_BUCKET=bmuz-covers-899012776854
MAIL_FROM=no-reply@bmuz.eu
SITE_URL=https://bmuz.eu
EOF

cat > /opt/bmuz/start.sh <<'EOF'
#!/bin/bash
# Чытае пароль базы з Secrets Manager пры кожным запуску і запускае вобраз.
set -euo pipefail
source /opt/bmuz/env.conf

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$IMAGE"

SECRET=$(aws secretsmanager get-secret-value --region "$REGION" --secret-id "$SECRET_ID" --query SecretString --output text)
export SPRING_DATASOURCE_USERNAME=$(printf '%s' "$SECRET" | python3 -c 'import json,sys; print(json.load(sys.stdin)["username"])')
export SPRING_DATASOURCE_PASSWORD=$(printf '%s' "$SECRET" | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')
export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:5432/bmuz?sslmode=require"
unset SECRET
# Вокладкі ў S3 і лісты праз SES; правы на іх праграма ў кантэйнеры бярэ з ролі сэрвэра.
export BMUZ_COVERS_BUCKET="$COVERS_BUCKET" BMUZ_MAIL_FROM="$MAIL_FROM" BMUZ_SITE_URL="$SITE_URL" \
  AWS_REGION="$REGION"

docker rm -f bmuz >/dev/null 2>&1 || true
exec docker run --name bmuz -p 80:8080 \
  -e SPRING_DATASOURCE_URL -e SPRING_DATASOURCE_USERNAME -e SPRING_DATASOURCE_PASSWORD \
  -e BMUZ_COVERS_BUCKET -e BMUZ_MAIL_FROM -e BMUZ_SITE_URL -e AWS_REGION \
  -e JAVA_TOOL_OPTIONS="-Xmx384m" \
  "$IMAGE" "$@"
EOF
chmod 700 /opt/bmuz/start.sh

cat > /etc/systemd/system/bmuz.service <<'EOF'
[Unit]
Description=BMUZ web site
After=docker.service network-online.target
Requires=docker.service
Wants=network-online.target

[Service]
ExecStart=/opt/bmuz/start.sh
ExecStop=/usr/bin/docker stop bmuz
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now bmuz
