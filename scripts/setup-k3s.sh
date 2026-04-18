#!/bin/bash
# =============================================================================
# scripts/setup-k3s.sh — Deploy k3s cluster
# =============================================================================
# Control plane: hmvlapk8s001 (10.10.1.61)
# Workers:       hmvlapk8s002 (10.10.1.62), hmvlapk8s003 (10.10.1.63)
#
# Run setup-banner-k8s.sh first if nodes were not provisioned via the pipeline.
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  playbooks/linux/setup_k3s.yml
