#!/bin/bash
# =============================================================================
# scripts/setup-monitoring-stack.sh — Deploy full monitoring stack
# =============================================================================
# Deploys all monitoring services in dependency order:
#   mon001 (10.10.1.51) — Grafana + node_exporter
#   mon002 (10.10.1.52) — Prometheus + node_exporter + snmp_exporter + vmware_exporter
#   mon003 (10.10.1.53) — Meraki exporter + UnPoller + node_exporter + Loki
#
# Safe to re-run — idempotent full rebuild.
#
# Usage:
#   ./scripts/setup-monitoring-stack.sh                          # full stack
#   ./scripts/setup-monitoring-stack.sh --limit hmvlapmon002.nnt.com  # single host
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  playbooks/linux/setup_monitoring_stack.yml \
  "$@"
