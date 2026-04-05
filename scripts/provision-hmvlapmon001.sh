#!/bin/bash
# =============================================================================
# scripts/provision-hmvlapmon001.sh — Provision Grafana VM
# =============================================================================
# hmvlapmon001 — Grafana
# IP:       10.10.1.51/23
# Host:     hsplv021.nnt.com
# Specs:    2vCPU / 8GB RAM
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook playbooks/provision_vm.yml \
  --extra-vars "
    vm_name=hmvlapmon001
    vm_hostname=hmvlapmon001
    vm_ip=10.10.1.51
    vm_esxi_host=hsplv021.nnt.com
    vm_datastore=LUNPLV163001
    vm_vcpus=2
    vm_memory_mb=8192
  "
