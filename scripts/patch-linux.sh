#!/bin/bash
# =============================================================================
# scripts/patch-linux.sh — Patch all managed Linux VMs
# =============================================================================
# Targets: monitoring, jenkins, nfs_servers, registry, vault_servers, k8s_nodes
# Excluded: ansible_nodes (ans001), plex_servers (OL7)
# Runs one host at a time (serial: 1). Reboots if kernel/core packages updated.
# Validates service health after reboot.
#
# Usage:
#   ./scripts/patch-linux.sh               # patch everything
#   ./scripts/patch-linux.sh --check       # dry run
#   ./scripts/patch-linux.sh --limit hmvlapk8s001.nnt.com  # single host
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  --vault-password-file ~/.ansible/vault_pass.txt \
  playbooks/linux/patch_linux_vms.yml \
  "$@"
