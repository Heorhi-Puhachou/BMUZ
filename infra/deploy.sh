# Выкладка новага вобраза на сэрвэры; яе дасылае GitHub Actions праз Systems Manager,
# дадаўшы перад гэтым тэкстам радок TAG=<кароткі хэш коміту>.
# Калі сайт не адказвае за 6 хвілін, вяртае ранейшы вобраз.
set -euo pipefail
source /opt/bmuz/env.conf
[[ "${TAG:-}" =~ ^[0-9a-f]{7,40}$ ]] || { echo "bad TAG: ${TAG:-}"; exit 1; }
NEW="$REGISTRY/bmuz:$TAG"

up() {
  for _ in $(seq 72); do
    sleep 5
    [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost/api/auth/me)" = 204 ] && return 0
  done
  return 1
}

echo "was: $IMAGE"
cp -p /opt/bmuz/env.conf /opt/bmuz/env.conf.previous
sed -i "s|^IMAGE=.*|IMAGE=$NEW|" /opt/bmuz/env.conf
systemctl restart bmuz
if ! up; then
  echo "$NEW did not come up; going back to $IMAGE"
  journalctl -u bmuz --since "-7 min" --no-pager | grep -E "ERROR|Exception" | tail -5 || true
  cp -p /opt/bmuz/env.conf.previous /opt/bmuz/env.conf
  systemctl restart bmuz
  up && echo "back on $IMAGE" || echo "previous image did not come up either"
  exit 1
fi
# Старыя вобразы — з дыска: пры патрэбе start.sh зноў цягне іх з ECR.
docker image prune -af >/dev/null
echo "live: $NEW"
