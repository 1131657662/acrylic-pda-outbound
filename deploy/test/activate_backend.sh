#!/usr/bin/env bash
# Activate the verified V28 backend without changing the separately deployed Web/downloads.
set -euo pipefail
test "$(id -u)" = 0
release="${1:?Pass the absolute verified and migrated backend release directory}"
[[ "$release" =~ ^/opt/acrylic-outbound-test/releases/[a-zA-Z0-9._-]+$ ]]
python3 "$release/deploy/verify_backend_release.py" "$release"

base=/opt/acrylic-outbound-test
exec 9>/run/lock/acrylic-outbound-test-backup.lock
flock -n 9
schema=$(mysql --defaults-file=/etc/acrylic-outbound-test/migration.cnf -NB acrylic_outbound_test \
  -e 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;')
[[ "$schema" == 28 ]]
test ! -e "$base/current.next" && test ! -L "$base/current.next"

stop_on_failure() {
  local result=$?
  trap - EXIT
  if ((result != 0)); then
    systemctl stop acrylic-outbound-test-worker.service || true
    systemctl stop acrylic-outbound-test.service || true
    echo 'Activation failed; writers stopped. Inspect evidence before any rollback.' >&2
  fi
  exit "$result"
}
trap stop_on_failure EXIT
if systemctl is-active --quiet acrylic-outbound-test-worker.service; then
  systemctl stop acrylic-outbound-test-worker.service
fi
systemctl stop acrylic-outbound-test.service
for name in acrylic-outbound-test.service acrylic-outbound-test-worker.service \
  acrylic-outbound-test-backup.service acrylic-outbound-test-backup.timer; do
  install -m 644 "$release/deploy/$name" /etc/systemd/system/
done
ln -s "$release" "$base/current.next"
mv -Tf "$base/current.next" "$base/current"
systemctl daemon-reload
systemctl start acrylic-outbound-test.service
ready=false
for attempt in $(seq 1 45); do
  if curl --connect-timeout 2 --max-time 3 -fsS -o /dev/null http://127.0.0.1:18085/api/v1/auth/csrf; then ready=true; break; fi
  sleep 2
done
if ! $ready; then
  systemctl stop acrylic-outbound-test.service
  echo 'Backend readiness failed; keep API/worker stopped and inspect additive migration compatibility.' >&2
  exit 1
fi
systemctl enable acrylic-outbound-test.service acrylic-outbound-test-worker.service
systemctl start acrylic-outbound-test-worker.service
sleep 5
systemctl is-active --quiet acrylic-outbound-test-worker.service
systemctl enable --now acrylic-outbound-test-backup.timer
printf 'Activated backend %s; Web and APK publication untouched.\n' "$release"
