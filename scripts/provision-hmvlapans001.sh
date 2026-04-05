#!/bin/bash
# =============================================================================
# scripts/provision-hmvlapans001.sh — Provision Ansible Control Node VM
# =============================================================================
# hmvlapans001 — Ansible Control Node
# IP:       10.10.1.31/23
# Host:     hsplv021.nnt.com
# Specs:    2vCPU / 4GB RAM
# NOTE:     Control node is already deployed — this script is for rebuild/recovery
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook playbooks/provision_vm.yml \
  --extra-vars "
    vm_name=hmvlapans001
    vm_hostname=hmvlapans001
    vm_ip=10.10.1.31
    vm_esxi_host=hsplv021.nnt.com
    vm_datastore=LUNPLV163001
    vm_vcpus=2
    vm_memory_mb=4096
  "
