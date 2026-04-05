#!/bin/bash
# =============================================================================
# scripts/provision-hmvlapmon002.sh — Provision Prometheus VM
# =============================================================================
# hmvlapmon002 — Prometheus
# IP:       10.10.1.52/23
# Host:     hsplv021.nnt.com
# Specs:    2vCPU / 8GB RAM
# =============================================================================

cd "$(dirname "$0")/.." || exit 1

ansible-playbook playbooks/provision_vm.yml \
  --extra-vars "
    vm_name=hmvlapmon002
    vm_hostname=hmvlapmon002
    vm_ip=10.10.1.52
    vm_esxi_host=hsplv021.nnt.com
    vm_datastore=LUNPLV163001
    vm_vcpus=2
    vm_memory_mb=8192
  "
