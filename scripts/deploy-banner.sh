#!/bin/bash
# =============================================================================
# scripts/deploy-banner.sh — Deploy login banner to all Linux VMs
# =============================================================================
# Pushes /etc/profile.d/00-banner.sh and /etc/issue.net to every managed host.
# Safe to re-run — idempotent.
#
# Usage:
#   ./scripts/deploy-banner.sh                              # all hosts
#   ./scripts/deploy-banner.sh --limit hmvlapmon002.nnt.com # single host
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  playbooks/linux/setup_login_banner.yml \
  "$@"
