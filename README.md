# homelabinfra-iac-ansible

Production-grade Infrastructure as Code for a VMware-based home lab. Built with Ansible, managed entirely through code — no manual configuration, no snowflake hosts.

---

## What This Is

A fully automated infrastructure stack running on VMware vSphere. Every VM is provisioned from a golden template, configured via Ansible, and monitored through a complete observability stack. All secrets are vault-encrypted. All state is in git.

This is not a tutorial repo. It's a working system.

---

## Infrastructure Overview

```
Network Layer
├── Cisco Catalyst 3750G        — core switching
├── Arista 7050SX               — spine (pending)
└── Meraki MX                   — edge/routing

Compute Layer (VMware vSphere)
├── vCenter                     — VM management
├── 3x ESXi hosts
└── All VMs cloned from golden OL9 template — zero manual touches

Services
├── Ansible control node
├── Jenkins CI/CD
└── Monitoring stack
    ├── Grafana                 — dashboards and visualization
    ├── Prometheus              — metrics collection and storage
    └── Loki                    — centralized log aggregation
```

---

## Roles

| Role | Purpose | Target |
|------|---------|--------|
| `common` | Timezone, NTP, SELinux, base packages | All Linux VMs |
| `provision_vm` | Zero-touch VM clone from vCenter template | vCenter API |
| `ansible_node` | Bootstrap Ansible control node | Control node VM |
| `jenkins` | Jenkins LTS + Docker CE | CI/CD VM |
| `prometheus` | Prometheus metrics collection | Monitoring VM |
| `grafana` | Grafana dashboards | Monitoring VM |
| `loki` | Centralized log aggregation | Monitoring VM |
| `node_exporter` | Host metrics agent | All Linux VMs |
| `promtail` | Log shipping agent | All Linux VMs |
| `vmware_exporter` | ESXi/vCenter metrics | Monitoring VM |

---

## Playbooks

```
playbooks/
├── provision_vm.yml            # Clone VM from template (use scripts/ wrappers)
├── setup_ansible_node.yml      # Bootstrap control node
├── setup_jenkins.yml           # Deploy Jenkins
├── setup_prometheus.yml        # Deploy Prometheus
├── setup_grafana.yml           # Deploy Grafana
├── setup_loki.yml              # Deploy Loki
├── setup_vmware_exporter.yml   # Deploy vmware_exporter
├── deploy_node_exporter.yml    # Deploy node_exporter to all Linux VMs
├── deploy_promtail.yml         # Deploy Promtail to all Linux VMs
├── network_backup.yml          # Back up network device configs to git
├── cisco_push.yml              # Push config to Cisco switches
├── arista_push.yml             # Push config to Arista switches
├── vlan_audit.yml              # Audit VLAN consistency
└── vrrp_validate.yml           # Validate VRRP state
```

---

## VM Provisioning

VMs are provisioned zero-touch using VMware Guest Customization. No cloud-init, no manual IP assignment.

```bash
# Provision a specific VM
./scripts/provision-grafana.sh        # Grafana VM
./scripts/provision-prometheus.sh     # Prometheus VM
./scripts/provision-loki.sh           # Loki VM
./scripts/provision-jenkins.sh        # Jenkins VM
./scripts/provision-ansible.sh        # Ansible control node
```

Each script sets hostname, static IP, and domain via vCenter guest customization, then waits for SSH to become available. The service playbook runs next.

**Template:** OL9.7, open-vm-tools, SSH key pre-loaded, cloud-init disabled.

---

## Disk Layout Standard

All VMs follow a consistent disk separation pattern:

| Disk | Size | Purpose |
|------|------|---------|
| OS disk | 30GB | `/boot`, LVM root, swap |
| Data disk | 60GB | Service data (metrics, logs, builds, etc.) |

VMware SCSI slot assignment is inconsistent — the data disk may be `sda` or `sdb` depending on the VM. All roles auto-detect the data disk at runtime by size (>50GB, unpartitioned). Nothing is hardcoded.

---

## Observability Stack

```
Metrics:  Prometheus (9090) ← node_exporter (9100) on all Linux VMs
                             ← vmware_exporter (9272) → vCenter API → ESXi hosts
                             ← snmp_exporter (9116) → Cisco / Arista
                             ← Jenkins metrics endpoint (8080/prometheus)

Logs:     Loki (3100) ← Promtail on all Linux VMs (systemd journal + /var/log)

Dashboards: Grafana (3000) → queries Prometheus + Loki
```

---

## Network Automation

Network device configs are backed up to a separate git repo on a schedule via Jenkins. Changes are committed with timestamps — full audit trail, no file server required.

Supported:
- Cisco IOS (Catalyst 3750G) — `ios_command` via SSH
- Arista EOS (7050SX) — `eos_command` via SSH (pending delivery)
- Meraki — API-based

---

## Secrets Management

All credentials are stored in ansible-vault encrypted files:

```
inventory/group_vars/*/vault.yml    # Encrypted, committed to git
~/.ansible/vault_pass.txt           # Never committed
```

Vault files are safe to commit once encrypted. The vault password file is machine-local only.

---

## Requirements

```bash
# Control node
ansible-core==2.15.13   # Pinned — OL9 Python 3.9, 2.16+ requires 3.10
python3-pip

# Collections
ansible-galaxy collection install community.vmware
ansible-galaxy collection install community.general
ansible-galaxy collection install ansible.posix
ansible-galaxy collection install community.network
```

---

## Repository Structure

```
.
├── ansible.cfg
├── inventory/
│   ├── linux/              # Linux VM inventory (by service)
│   ├── network/            # Network device inventory
│   ├── compute/            # ESXi hosts and vCenter
│   ├── group_vars/         # Group variables + encrypted vaults
│   └── host_vars/          # Host-specific overrides
├── playbooks/              # Top-level playbooks
├── roles/                  # Ansible roles
└── scripts/                # VM provisioning wrapper scripts
```

---

## Design Principles

- **Everything is code.** No manual steps, no undocumented configuration.
- **Idempotent by default.** Every playbook is safe to re-run.
- **Secrets never in plaintext.** All credentials vault-encrypted before commit.
- **Data separate from OS.** Every service role mounts a dedicated data disk.
- **Zero-touch provisioning.** VM clone to SSH-ready in under 10 minutes, no human in the loop.
