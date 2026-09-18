#!/usr/bin/env bash
set -euo pipefail
test "$(id -u)" = 0
release="${1:?Pass the absolute verified and migrated release directory}"
[[ "$release" =~ ^/opt/acrylic-outbound-test/releases/[a-zA-Z0-9._-]+$ ]]
test -f "$release/server.jar"
test -f "$release/web/index.html"
test -f /etc/letsencrypt/live/outbound-test.nbsmzwl.cn/fullchain.pem
base=/opt/acrylic-outbound-test
previous=''
if [[ -L "$base/current" ]]; then previous="$(readlink -f "$base/current")"; fi
install -m 644 "$release/deploy/acrylic-outbound-test.service" /etc/systemd/system/
install -m 644 "$release/deploy/acrylic-outbound-test-backup.service" /etc/systemd/system/
install -m 644 "$release/deploy/acrylic-outbound-test-backup.timer" /etc/systemd/system/
install -m 644 "$release/deploy/proxy.conf" /etc/acrylic-outbound-test/
install -m 644 "$release/deploy/security-headers.conf" /etc/acrylic-outbound-test/
ln -s "$release" "$base/current.next"
mv -Tf "$base/current.next" "$base/current"
systemctl daemon-reload
systemctl enable acrylic-outbound-test.service
systemctl restart acrylic-outbound-test.service
ready=false
for attempt in $(seq 1 45); do
  if curl -fsS -o /dev/null http://127.0.0.1:18085/api/v1/auth/csrf; then
    ready=true
    break
  fi
  sleep 2
done
if ! $ready; then
  systemctl stop acrylic-outbound-test.service
  echo 'New release failed readiness. Keep writes stopped; inspect schema compatibility before rollback.' >&2
  exit 1
fi
site=/etc/nginx/sites-available/acrylic-outbound-test
if [[ -f "$site" ]]; then cp -p "$site" "$site.previous"; fi
install -m 644 "$release/deploy/nginx.conf" "$site"
ln -sfn "$site" /etc/nginx/sites-enabled/acrylic-outbound-test
if ! nginx -t; then
  if [[ -f "$site.previous" ]]; then mv "$site.previous" "$site"; fi
  echo 'Nginx was not reloaded; inspect the test site before retrying.' >&2
  exit 1
fi
systemctl reload nginx
systemctl enable --now acrylic-outbound-test-backup.timer
printf 'Activated %s; previous=%s\n' "$release" "$previous"
