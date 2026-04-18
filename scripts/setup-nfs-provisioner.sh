#!/bin/bash
# =============================================================================
# scripts/setup-nfs-provisioner.sh — Deploy k8s NFS storage provisioner
# =============================================================================
# Installs nfs-utils on all k8s nodes, then deploys nfs-subdir-external-
# provisioner via Helm on the control plane. Creates StorageClass "nfs"
# as the cluster default backed by hmvlapnfs001:/exports/k8s.
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  playbooks/linux/setup_nfs_provisioner.yml
