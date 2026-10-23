#!/bin/bash
# Першы запуск сэрвэра BMZ (Amazon Linux 2023): Docker, swap і сэрвіс bmz.
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

mkdir -p /opt/bmz

cat > /opt/bmz/env.conf <<'EOF'
REGION=eu-north-1
REGISTRY=899012776854.dkr.ecr.eu-north-1.amazonaws.com
IMAGE=899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmz:483cf7a
DB_HOST=bmz.cbas4a8c0z88.eu-north-1.rds.amazonaws.com
SECRET_ID=arn:aws:secretsmanager:eu-north-1:899012776854:secret:rds!db-05036461-bccc-443f-be0e-add12aaf16cc-RKSCCe
EOF

cat > /opt/bmz/start.sh <<'EOF'
#!/bin/bash
# Чытае пароль базы з Secrets Manager пры кожным запуску і запускае вобраз.
set -euo pipefail
source /opt/bmz/env.conf

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$IMAGE"

SECRET=$(aws secretsmanager get-secret-value --region "$REGION" --secret-id "$SECRET_ID" --query SecretString --output text)
export SPRING_DATASOURCE_USERNAME=$(printf '%s' "$SECRET" | python3 -c 'import json,sys; print(json.load(sys.stdin)["username"])')
export SPRING_DATASOURCE_PASSWORD=$(printf '%s' "$SECRET" | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')
export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:5432/bmz?sslmode=require"
unset SECRET

docker rm -f bmz >/dev/null 2>&1 || true
exec docker run --name bmz -p 80:8080 \
  -e SPRING_DATASOURCE_URL -e SPRING_DATASOURCE_USERNAME -e SPRING_DATASOURCE_PASSWORD \
  -e JAVA_TOOL_OPTIONS="-Xmx384m" \
  "$IMAGE" "$@"
EOF
chmod 700 /opt/bmz/start.sh

cat > /etc/systemd/system/bmz.service <<'EOF'
[Unit]
Description=BMZ web site
After=docker.service network-online.target
Requires=docker.service
Wants=network-online.target

[Service]
ExecStart=/opt/bmz/start.sh
ExecStop=/usr/bin/docker stop bmz
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now bmz
