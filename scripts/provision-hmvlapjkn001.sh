#!/bin/bash
# =============================================================================
# scripts/provision-hmvlapjkn001.sh — Provision Jenkins VM
# =============================================================================
# hmvlapjkn001 — Jenkins CI/CD
# IP:       10.10.1.41/23
# Host:     hsplv021.nnt.com
# Specs:    4vCPU / 16GB RAM
# NOTE:     Jenkins is already deployed — this script is for rebuild/recovery
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook playbooks/provision_vm.yml \
  --extra-vars "
    vm_name=hmvlapjkn001
    vm_hostname=hmvlapjkn001
    vm_ip=10.10.1.41
    vm_esxi_host=hsplv021.nnt.com
    vm_datastore=LUNPLV163001
    vm_vcpus=4
    vm_memory_mb=16384
  "
