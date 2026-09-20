#!/usr/bin/env bash
# Source from root-only maintenance scripts; restore only services that were running.
active_services=()
resume_services=true

restore_services() {
  local result=$? service
  trap - EXIT
  if $resume_services; then
    for service in "${active_services[@]}"; do
      if ! systemctl start "$service"; then
        echo "Could not restore $service after maintenance" >&2
        result=1
      fi
    done
  fi
  exit "$result"
}

stop_writers() {
  local service index
  for service in acrylic-outbound-test.service acrylic-outbound-test-worker.service; do
    if systemctl is-active --quiet "$service"; then active_services+=("$service"); fi
  done
  trap restore_services EXIT
  # Stop the worker first; resume API first. Missing/disabled workers stay disabled.
  for ((index=${#active_services[@]}-1; index>=0; index--)); do
    systemctl stop "${active_services[index]}"
  done
}
