#!/bin/bash
# =============================================================================
# scripts/setup-banner-k8s.sh — Apply login banner to k8s nodes
# =============================================================================
# Targets: hmvlapk8s001/002/003 (10.10.1.61-63)
# Run this before deploying k3s — nodes were provisioned outside the pipeline.
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  -l k8s_nodes \
  playbooks/linux/setup_login_banner.yml
