#!/usr/bin/env bash
# Test service-state behavior with a command boundary stub; no system services touched.
set -euo pipefail
source_file="$(cd "$(dirname "$0")" && pwd)/service_checkpoint.sh"
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
export SERVICE_TEST_LOG="$scratch/events"

systemctl() {
  if [[ "$1" == is-active ]]; then
    [[ " $SERVICE_TEST_ACTIVE " == *" $3 "* ]]
    return
  fi
  printf '%s %s\n' "$1" "$2" >> "$SERVICE_TEST_LOG"
  [[ "${SERVICE_TEST_FAIL_STOP:-}" != "$1 $2" ]]
}
export -f systemctl

check_case() {
  local name="$1" body="$2" expected="$3" status
  : > "$SERVICE_TEST_LOG"
  status=0
  bash -euo pipefail -c "source \"\$1\"; stop_writers; $body" -- "$source_file" || status=$?
  [[ "$(cat "$SERVICE_TEST_LOG")" == "$expected" ]] || { echo "FAILED: $name sequence"; exit 1; }
  [[ "$status" == "$expected_status" ]] || { echo "FAILED: $name status $status"; exit 1; }
  printf 'PASS %s\n' "$name"
}

export SERVICE_TEST_ACTIVE='acrylic-outbound-test.service acrylic-outbound-test-worker.service'
both=$'stop acrylic-outbound-test-worker.service\nstop acrylic-outbound-test.service\nstart acrylic-outbound-test.service\nstart acrylic-outbound-test-worker.service'
expected_status=0
check_case success ':' "$both"
expected_status=7
check_case backup_failure 'exit 7' "$both"
expected_status=0
check_case keep_stopped 'resume_services=false' $'stop acrylic-outbound-test-worker.service\nstop acrylic-outbound-test.service'
export SERVICE_TEST_ACTIVE='acrylic-outbound-test.service'
check_case worker_absent ':' $'stop acrylic-outbound-test.service\nstart acrylic-outbound-test.service'
export SERVICE_TEST_ACTIVE='acrylic-outbound-test-worker.service'
check_case api_stopped ':' $'stop acrylic-outbound-test-worker.service\nstart acrylic-outbound-test-worker.service'
export SERVICE_TEST_ACTIVE=''
check_case all_stopped ':' ''
export SERVICE_TEST_ACTIVE='acrylic-outbound-test.service acrylic-outbound-test-worker.service'
export SERVICE_TEST_FAIL_STOP='stop acrylic-outbound-test.service'
expected_status=1
check_case partial_stop_failure ':' "$both"
