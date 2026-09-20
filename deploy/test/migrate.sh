#!/usr/bin/env bash
set -euo pipefail
test "$(id -u)" = 0
release="${1:?Pass the absolute release directory}"
[[ "$release" =~ ^/opt/acrylic-outbound-test/releases/[a-zA-Z0-9._-]+$ ]]
# Older full-stack packages remain supported; new backend packages require integrity checks.
if [[ -f "$release/deploy/verify_backend_release.py" ]]; then
  python3 "$release/deploy/verify_backend_release.py" "$release"
else
  test -f "$release/server.jar"
fi
if [[ -L /opt/acrylic-outbound-test/current ]]; then
  "$release/deploy/backup.sh" --keep-stopped
  systemctl stop acrylic-outbound-test.service
fi
exec 9>/run/lock/acrylic-outbound-test-backup.lock
flock -n 9
set -a
source /etc/acrylic-outbound-test/runtime.env
source /etc/acrylic-outbound-test/migration.env
set +a
export OUTBOUND_MIGRATE=true
java -Duser.timezone=UTC -Xms64m -Xmx384m -jar "$release/server.jar" \
  --spring.main.web-application-type=none --outbound.worker.enabled=false --outbound.exit-after-startup=true
if [[ "${2:-}" == "--bootstrap-empty" ]]; then
  set -a
  source /etc/acrylic-outbound-test/bootstrap.env
  set +a
  java -Duser.timezone=UTC -Xms64m -Xmx384m -jar "$release/server.jar" \
    --spring.main.web-application-type=none --outbound.worker.enabled=false --outbound.bootstrap.enabled=true
fi
