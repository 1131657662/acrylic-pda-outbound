#!/usr/bin/env bash
set -euo pipefail
test "$(id -u)" = 0
umask 077
mode="${1:-}"
[[ -z "$mode" || "$mode" == '--keep-stopped' ]]
base=/opt/backups/acrylic-outbound-test
mkdir -p "$base"
exec 9>/run/lock/acrylic-outbound-test-backup.lock
flock -n 9
destination="$base/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir "$destination"
active=false
if systemctl is-active --quiet acrylic-outbound-test.service; then
  active=true
  systemctl stop acrylic-outbound-test.service
fi
trap 'if $active; then systemctl start acrylic-outbound-test.service; fi' EXIT
mysqldump --defaults-file=/etc/acrylic-outbound-test/migration.cnf \
  --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF \
  --hex-blob acrylic_outbound_test | gzip > "$destination/database.sql.gz"
python3 "$(dirname "$0")/backup_facts.py" > "$destination/database-facts.json"
tar -czf "$destination/private-files-and-config.tar.gz" \
  -C / etc/acrylic-outbound-test var/lib/acrylic-outbound-test/files
readlink -f /opt/acrylic-outbound-test/current > "$destination/release.txt"
cp /opt/acrylic-outbound-test/current/manifest.json "$destination/manifest.json"
(cd "$destination" && sha256sum database.sql.gz database-facts.json private-files-and-config.tar.gz release.txt manifest.json > SHA256SUMS)
if [[ "$mode" == '--keep-stopped' ]]; then active=false; fi
printf '%s\n' "$destination"
