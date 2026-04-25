# homelabinfra-iac-ansible

Production-grade Infrastructure as Code for a VMware-based private cloud. Every host is provisioned from code, configured from code, monitored from code, and patched from code. Nothing in this environment exists outside of version control.

This repository is the configuration management and automation layer for the NNT homelab. It works alongside two companion repositories:

- **homelabinfra-iac-network** — Network device configs for Cisco IOS, Arista EOS, and Meraki. Configuration-as-code for all switching infrastructure, backed up to git on a daily schedule via Jenkins.
- **homelabinfra-iac-terraform** *(in progress)* — VMware provisioning layer. Terraform replaces ad-hoc VM provisioning with declarative infrastructure definitions, plan-before-apply change control, and remote state stored in Minio.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          nanonetech.com Domain                          │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Network Layer                                                    │   │
│  │  Meraki MX64 (edge/WAN) → Arista 7050SX (spine, VRRP master)    │   │
│  │                         → Arista 7048T  (VRRP secondary)         │   │
│  │                         → Cisco 3750G   (VRRP backup)            │   │
│  │  VRRP across 3 L3 switches — no single point of failure at L3   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                 │                                        │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Compute Layer — VMware vSphere (3x ESXi hosts + vCenter 7)      │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  Identity & PKI                                          │    │   │
│  │  │  AD DC (nanonetech.com) — DNS, GPO, centralized auth     │    │   │
│  │  │  ADCS Root CA (offline-capable) → ADCS Sub-CA (issuing)  │    │   │
│  │  │  Sub-CA → VMCA (subordinate) → vCenter + ESXi certs      │    │   │
│  │  │  Sub-CA → Harbor TLS / Vault TLS / Linux trust store     │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  CI/CD & Automation                                       │    │   │
│  │  │  Jenkins (pipelines) ──► Ansible (hmvlapans001)           │    │   │
│  │  │  All infrastructure changes flow through pipelines        │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  Observability                                            │    │   │
│  │  │  Prometheus ◄── node_exporter (all Linux VMs)            │    │   │
│  │  │            ◄── vmware_exporter (vCenter/ESXi)            │    │   │
│  │  │            ◄── snmp_exporter (Cisco + Arista)            │    │   │
│  │  │            ◄── meraki_exporter (Meraki REST API)         │    │   │
│  │  │            ◄── unpoller (UniFi APs)                      │    │   │
│  │  │  Loki ◄── Promtail (all Linux VMs)                       │    │   │
│  │  │  Grafana → Prometheus + Loki                             │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  Platform Services                                        │    │   │
│  │  │  Harbor (OCI registry)   HashiCorp Vault (secrets)       │    │   │
│  │  │  NetBox (IPAM/DCIM)      NFS (k8s persistent volumes)    │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  Kubernetes (k3s)                                         │    │   │
│  │  │  1 control plane + 2 workers                             │    │   │
│  │  │  Default StorageClass: NFS (dynamic provisioning)        │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Backup — Veeam Backup & Replication                             │   │
│  │  All VMs protected. 3-2-1 strategy.                             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Infrastructure Inventory

### Linux VMs

| VM | IP | Role |
|---|---|---|
| hmvlapans001.nnt.com | 10.10.1.31 | Ansible control node |
| hmvlapjkn001.nnt.com | 10.10.1.41 | Jenkins CI/CD |
| hmvlapmon001.nnt.com | 10.10.1.51 | Grafana |
| hmvlapmon002.nnt.com | 10.10.1.52 | Prometheus |
| hmvlapmon003.nnt.com | 10.10.1.53 | Loki / Meraki exporter |
| hmvlapnfs001.nnt.com | 10.10.0.42 | NFS server (k8s persistent volumes, 250GB XFS) |
| hmvlapreg001.nnt.com | 10.10.0.43 | Harbor container registry |
| hmvlapvlt001.nnt.com | 10.10.0.44 | HashiCorp Vault |
| hmvlapk8s001.nnt.com | 10.10.1.61 | k3s control plane |
| hmvlapk8s002.nnt.com | — | k3s worker |
| hmvlapk8s003.nnt.com | — | k3s worker |
| hmvlapweb001.nnt.com | 10.10.0.41 | Nginx webserver |

### Windows VMs

| VM | IP | Role |
|---|---|---|
| hmvwapdc001.nanonetech.com | 10.10.0.11 | Active Directory DC / DNS |
| hmvwapca001.nanonetech.com | 10.10.0.14 | ADCS Root CA (offline-capable) |
| hmvwapca002.nanonetech.com | 10.10.0.15 | ADCS Subordinate / Issuing CA |
| hmvwapexc001.nanonetech.com | — | Exchange Server |
| hmvlapvc001.nnt.com | 10.10.0.5 | VMware vCenter 7 |

### Network Devices

| Device | Model | IP | Role |
|---|---|---|---|
| swplnet251.nnt.com | Cisco Catalyst 3750G | 10.100.100.251 | L3 switch, VRRP backup (priority 90) |
| swplnet252.nnt.com | Arista 7050SX-64 | 10.100.100.252 | L3 switch, VRRP master (priority 110) |
| swplnet253.nnt.com | Arista 7048T-A | 10.100.100.253 | L3 switch, VRRP secondary (priority 100) |
| — | Meraki MX64 | WAN: 184.57.35.211 | Edge / WAN / stateful firewall / routing |

### ESXi Hosts

| Host | IP | Certificate |
|---|---|---|
| hsplv021.nnt.com | 10.100.254.21 | Issued by NNT-NANONETECH-Sub-CA, valid to Apr 2028 |
| hsplv022.nnt.com | 10.100.254.22 | Issued by NNT-NANONETECH-Sub-CA, valid to Apr 2028 |
| hsplv034.nnt.com | 10.100.254.34 | Issued by NNT-NANONETECH-Sub-CA, valid to Apr 2028 |

---

## PKI Architecture

The lab runs a two-tier certificate hierarchy modeled after enterprise PKI design. Every TLS certificate in the environment — from ESXi host identity to container registry to secrets management — chains to a CA under our control.

```
NNT-NANONETECH-CA  (Root CA)
│  Self-signed | Valid: 2026–2046
│  hmvwapca001 | Designed to be taken offline after Sub-CA issuance
│
└── NNT-NANONETECH-Sub-CA  (Subordinate / Issuing CA)
    │  Valid: 2026–2031 | hmvwapca002 | Always online
    │  Issues all end-entity certs via ADCS + WebServer template
    │
    ├── VMCA  (VMware CA — made subordinate to Sub-CA)
    │   │  Valid: 2026–2031 | Installed on vCenter
    │   └── vCenter Machine SSL cert
    │
    ├── ESXi host certs  (hsplv021, hsplv022, hsplv034)
    │   Manually issued per host — CSR on ESXi, signed by Sub-CA
    │
    ├── Harbor TLS cert
    ├── Vault TLS cert  (pending)
    └── Linux CA trust store
        Distributed via distribute_root_ca.yml to all managed Linux hosts
```

**Why a two-tier hierarchy with an offline root?**
The root CA's only operational function is to sign the subordinate CA certificate. Everything else flows through the sub-CA. If the sub-CA is compromised or needs replacement, a new sub-CA can be issued from the root without invalidating the root trust anchor already distributed to every client. The offline root is a risk containment decision — you cannot compromise a CA that isn't reachable.

**Why make VMCA a subordinate CA instead of issuing all vSphere certs directly from Sub-CA?**
VMCA manages an internal ecosystem of solution user certificates, service TLS, and host identity certs that would require significant manual overhead to issue individually from an external CA. Making VMCA subordinate to our PKI brings the entire vSphere certificate chain under our control — every internal vCenter cert chains to NNT-NANONETECH-CA — without taking on the operational burden of manually managing dozens of internal certs.

---

## Identity & Authentication

All Linux VMs are domain-joined to `nanonetech.com` via `realmd` + SSSD. Authentication is centralized through Active Directory. Local user accounts are minimized to the root account and service accounts only.

```
AD (nanonetech.com)
├── DNS — authoritative for nnt.com and nanonetech.com
├── Group Policy — Windows host configuration enforcement
├── SSSD (Linux) — PAM/NSS integration for all managed Linux VMs
│   └── "NNT Linux Admins" AD group → sudo rights on all Linux hosts
└── ADCS — PKI issuance for the entire lab
```

**Why AD-integrated Linux auth?**
Access control changes happen in one place. Add or remove someone from the "NNT Linux Admins" group in AD, and the change propagates to all 12 Linux VMs at next login — no Ansible run required, no per-host user management, no SSH key rotation across a fleet. This is how enterprise Linux fleets are managed at scale, and this lab reflects that pattern.

---

## CI/CD — Jenkins Pipelines

Ansible playbooks are not run by hand in production. Everything that touches live infrastructure runs through a Jenkins pipeline with a full audit trail — who triggered it, what changed, what the output was.

| Pipeline | Trigger | What it does |
|---|---|---|
| `nnt-jkn-lin-patch` | Weekly (scheduled) | `dnf update` all Linux VMs, reboot if required, validate all services post-reboot |
| `nnt-jkn-provision-vm` | On demand | Clone VM from golden template via vCenter API, set hostname/IP/DNS, wait for SSH readiness |
| `nnt-jkn-setup-k3s` | On demand | Bootstrap k3s control plane and join workers to cluster |
| `nnt-jkn-net-backup` | Daily (scheduled) | Pull running configs from all network devices, commit to git with timestamp |
| `nnt-jkn-net-enforce-swplnet251/252/253` | On demand | Push and verify baseline config against a specific switch |
| `nnt-jkn-win-dns-sync` | On demand | Sync DNS A records from NetBox IPAM to Active Directory DNS |

**Why Jenkins for infrastructure automation instead of a SaaS CI platform?**
All pipelines target on-premises infrastructure — vCenter, WinRM endpoints, SSH, network device SSH — none of which are reachable from a cloud-hosted runner without a VPN or tunnel. A self-hosted Jenkins instance on the same L2 segment eliminates that dependency. Jenkins also gives full control over pipeline agent configuration, credentials management, and job scheduling with no per-minute billing.

---

## Observability Stack

All metrics, logs, and dashboards are self-hosted. No telemetry leaves the lab. The stack is designed so that a failure in any single component does not take down the others.

### Metrics Pipeline

```
node_exporter     (port 9100)  ── all Linux VMs ──────────────────────┐
vmware_exporter   (port 9272)  ── vCenter API → ESXi host metrics ────┤
snmp_exporter     (port 9116)  ── Cisco 3750G + Arista switches ───────┤──► Prometheus (9090)
meraki_exporter   (custom)     ── Meraki MX64 REST API ────────────────┤     hmvlapmon002
unpoller          (port 9130)  ── UniFi AP metrics ────────────────────┤
Jenkins metrics   (port 8080)  ── build queue, executor utilization ───┘
```

### Log Pipeline

```
Promtail (all Linux VMs)  ──────────────────────────────► Loki (3100)
  Sources: systemd journal                                  hmvlapmon003
           /var/log/* per host
```

### Visualization

```
Grafana (3000) on hmvlapmon001
  Datasources: Prometheus + Loki
  Dashboards:  Linux host metrics, VMware/ESXi, Cisco/Arista SNMP,
               Meraki WAN/SD-WAN, UniFi wireless, Jenkins build health
```

**Why three separate monitoring VMs?**
Prometheus is write-heavy (constant scrape cycles, TSDB compaction). Grafana is read-heavy (query bursts when dashboards load). Loki is ingest-heavy (continuous log writes from all hosts). Co-locating these workloads masks resource contention, makes capacity planning ambiguous, and means restarting one component affects everything. Dedicated VMs give clean resource attribution and independent restart capability.

---

## Platform Services

### HashiCorp Vault (`hmvlapvlt001`)
Secrets management for the lab. Initialized and unsealed. Unseal keys are stored offline and are not in this repository. Intended use: dynamic secrets issuance, PKI intermediate CA, TLS cert storage for platform services, and eventually app-layer secrets for Kubernetes workloads.

### Harbor Container Registry (`hmvlapreg001`)
Self-hosted OCI-compliant container registry. TLS cert issued by NNT-NANONETECH-Sub-CA — trusted by all lab hosts. All container images used by Kubernetes workloads are pulled from Harbor. There is no runtime dependency on Docker Hub, quay.io, or any external registry. This means a WAN outage does not affect running or rescheduled workloads.

### NFS Server (`hmvlapnfs001`)
Dedicated NFS server providing 250GB of XFS-formatted persistent volume storage for the k3s cluster. Consumed by the `nfs-subdir-external-provisioner` running inside k3s. This is the cluster's default StorageClass — any workload that requests a PersistentVolumeClaim gets a dynamically provisioned subdirectory on this server with no manual intervention.

### NetBox (`hmvlapnet001`)
IPAM and DCIM source of truth for the lab. Every IP allocation is tracked in NetBox. The `nnt-jkn-win-dns-sync` Jenkins pipeline reads NetBox and pushes DNS A records to Active Directory DNS, eliminating manual DNS management and keeping IPAM and DNS in sync automatically.

---

## Kubernetes (k3s)

Lightweight production-grade Kubernetes on OL9.7. Three-node cluster: one control plane, two workers.

```
hmvlapk8s001  — k3s server (control plane)
hmvlapk8s002  — k3s agent  (worker)
hmvlapk8s003  — k3s agent  (worker)
```

**Storage design:**
The `local-path` StorageClass default is explicitly disabled and patched out. All persistent storage flows through the NFS StorageClass (`nfs-subdir-external-provisioner`). This is a deliberate decision: local-path storage is node-local, meaning a pod rescheduled to a different worker loses its data. NFS-backed storage survives node failure and pod rescheduling transparently.

**Container images:**
All workload images are pulled from Harbor (`hmvlapreg001`). The k3s cluster is configured with Harbor as the registry mirror — external registries are not in the critical path.

**Why k3s and not kubeadm?**
k3s ships as a single binary with an embedded SQLite datastore suitable for clusters below ~10 nodes. A kubeadm-managed cluster at this scale requires an external etcd cluster (3 nodes minimum for HA), a separate load balancer for the API server, and significantly more operational overhead to maintain. k3s is production software — it runs in production edge deployments at scale. It is not a simplified or restricted version of Kubernetes; it is a full-conformant distribution with a lower operational surface area.

---

## Backup

All VMs are protected by **Veeam Backup & Replication** following a 3-2-1 strategy:
- **3** copies of data
- **2** different storage media types
- **1** logically isolated copy

Veeam configuration is managed separately from this Ansible repo. Its existence is called out here because any infrastructure runbook that omits backup context is incomplete — these two concerns are tightly coupled operationally.

---

## Network Automation

Network device configurations are version-controlled in the companion **homelabinfra-iac-network** repository. This separation is intentional:

- Network configs use a different tooling chain (`cisco.ios`, `arista.eos` collections), different change velocity, and different review criteria than Linux/Windows automation.
- Mixing network and compute automation in one repo creates blast radius risk — a syntax error in a playbook targeting Linux hosts should not be in the same pipeline that can push a broken config to a switch.
- The network repo maintains its own commit history, giving a complete audit trail of every switch config change with before/after diffs.

**What this repo handles for networking:**
- `network_backup.yml` — triggers the Jenkins backup pipeline, which pulls running configs and commits them to the network repo
- `vlan_audit_swplnet*.yml` — validates VLAN consistency across all three switches
- `vrrp_validate.yml` — verifies VRRP master/backup state matches expected priority
- `enforce_baseline_swplnet*.yml` — pushes and verifies baseline config against a specific switch

**Device support:**

| Platform | Collection | Transport |
|---|---|---|
| Cisco Catalyst 3750G (IOS 12.x) | `cisco.ios` | SSH (legacy KEX — must run from ans001, not Mac) |
| Arista 7050SX / 7048T (EOS 4.x) | `arista.eos` | SSH |
| Meraki MX64 | REST API | HTTPS (Meraki SDK) |

---

## VM Provisioning

VMs are provisioned zero-touch using the `provision_vm` role, which wraps `community.vmware.vmware_guest`. Every VM is cloned from a sealed golden template with VMware Guest Customization handling hostname, static IP assignment, and AD DNS registration at clone time.

**Golden Template: `PLTMPOL904242026`**

| Item | Value |
|---|---|
| OS | Oracle Linux 9.7, fully patched at build date |
| Packages | `open-vm-tools`, `cloud-init`, `perl`, `python3`, `dnf-utils`, `chrony` |
| cloud-init | VMware datasource only; `network: config: disabled` |
| SSH | Key-based only, root, `id_ed25519` pre-loaded |
| Sealed | machine-id cleared, SSH host keys removed, cloud-init state cleaned |

**Why `cloud-init` with `network: config: disabled`?**
cloud-init with the VMware datasource handles post-clone injection (hostname, SSH keys). Disabling its network management prevents it from overwriting the static IP applied by VMware Guest Customization on first boot — a race condition that would make the VM unreachable without console access. Guest Customization owns IP configuration; cloud-init owns everything else.

**Disk layout — all VMs:**

| Disk | Mount | Purpose |
|---|---|---|
| OS disk (30–60GB) | `/`, `/boot` | Operating system, LVM root, swap |
| Data disk (60–250GB) | `/data` | Service-specific data — TSDB, logs, build artifacts, registry blobs, etc. |

VMware SCSI slot assignment is non-deterministic across clones. All roles detect the data disk at runtime by finding the largest unpartitioned disk — no device names are hardcoded, and `fstab` uses UUID exclusively.

---

## Roles Reference

| Role | Purpose | Target |
|---|---|---|
| `common` | NTP (chrony), timezone, SELinux, base packages, login banner | All Linux VMs |
| `login_banner` | SSH/console legal banner, hostname display | All Linux VMs |
| `provision_vm` | VMware guest clone + customization via vCenter API | vCenter |
| `ansible_node` | Bootstrap control node — pip, collections, vault | ans001 |
| `ad_join` | `realmd` + SSSD domain join, sudo group config | All Linux VMs |
| `jenkins` | Jenkins LTS + Java 17, RBAC, initial job seed | jkn001 |
| `prometheus` | Prometheus binary, config, systemd, retention | mon002 |
| `grafana` | Grafana, datasource provisioning | mon001 |
| `loki` | Loki binary, config, systemd | mon003 |
| `node_exporter` | Prometheus node_exporter, systemd | All Linux VMs |
| `promtail` | Loki Promtail, journal + file scrape config | All Linux VMs |
| `vmware_exporter` | vmware_exporter Python app, vCenter API credentials | mon002 |
| `snmp_exporter` | SNMP exporter for Cisco/Arista metrics | mon002 |
| `meraki_exporter` | Custom Meraki REST API → Prometheus exporter | mon003 |
| `unpoller` | UnPoller for UniFi AP metrics | mon003 |
| `harbor` | Harbor OCI registry, TLS, config | reg001 |
| `vault` | HashiCorp Vault binary, config, systemd | vlt001 |
| `netbox` | NetBox IPAM/DCIM, PostgreSQL, gunicorn, nginx | netbox VM |
| `nfs_server` | NFS export config, firewall, XFS mount | nfs001 |
| `k3s` | k3s install, control plane init, worker join | k8s nodes |
| `k8s_nfs_provisioner` | Helm deploy of nfs-subdir-external-provisioner | k8s control plane |
| `plex` | Plex Media Server, LVM data disk, config | plex VMs |
| `webserver` | Nginx, static site config | web001 |
| `windows_dc` | AD DS promotion, DNS zone config | DC VMs |
| `exchange` | Exchange Server configuration | exc001 |
| `mailcleaner` | Mailcleaner mail filter (Debian, SSH workarounds) | mailcleaner VM |
| `idrac_fan_controller` | Dell iDRAC fan speed override via IPMI | Physical hosts |

---

## Secrets Management

All credentials are encrypted with `ansible-vault` before commit. The vault password lives only on the Ansible control node and is never committed.

```
inventory/group_vars/*/vault.yml     # Encrypted — safe to commit
inventory/host_vars/*/vault.yml      # Encrypted — safe to commit
~/.ansible/vault_pass.txt            # NEVER committed — control node only
```

Running any playbook:

```bash
ansible-playbook playbooks/<category>/<playbook>.yml \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt
```

Targeting a single host:

```bash
ansible-playbook playbooks/linux/patch_linux_vms.yml \
  -i inventory/ \
  --vault-password-file ~/.ansible/vault_pass.txt \
  --limit hmvlapmon001.nnt.com
```

---

## Repository Structure

```
.
├── ansible.cfg                         # Inventory path, vault config, SSH settings
├── Jenkinsfile                         # Pipeline definition for Jenkins
├── requirements.yml                    # Ansible Galaxy collection dependencies
│
├── inventory/
│   ├── linux/                          # Linux VM groups by service role
│   ├── windows/                        # Windows VM groups by role
│   ├── network/                        # Network device groups
│   ├── compute/                        # ESXi hosts and vCenter
│   ├── group_vars/                     # Variables + encrypted vaults per group
│   └── host_vars/                      # Per-host variable overrides
│
├── playbooks/
│   ├── infra/                          # VM lifecycle — provision, destroy, cert issuance
│   ├── linux/                          # Linux service deployment and operations
│   ├── monitoring/                     # Observability stack deployment
│   ├── network/                        # Network backup, push, audit, validation
│   └── windows/                        # AD, DNS, PKI, Exchange automation
│
├── roles/                              # One role per service — see Roles Reference above
│
├── jenkins/                            # Groovy pipeline definitions (checked in, seeded to Jenkins)
│   ├── infra/
│   ├── linux/
│   ├── network/
│   └── windows/
│
├── scripts/                            # One-shot bootstrap wrapper scripts
│   ├── provision-*.sh                  # Per-VM provisioning wrappers
│   └── setup-*.sh                      # Per-service setup wrappers
│
└── docs/
    └── lessons-learned.md              # Hard-won operational knowledge — read before touching PKI or k3s
```

---

## Requirements

```bash
# Control node: hmvlapans001 (OL9.7)
ansible-core==2.15.13    # Pinned — see note below

# Install all required collections
ansible-galaxy collection install -r requirements.yml
```

**On the ansible-core version pin:**
OL9 ships Python 3.9 as the system interpreter. ansible-core 2.16 dropped support for Python 3.9 on the controller. Upgrading requires either installing Python 3.10+ alongside the system Python or migrating the control node. This is tracked — it is a deliberate constraint, not an oversight. Bump ansible-core only after validating the Python upgrade path on ans001.

---

## Design Principles

**Everything in version control.** No configuration exists only in a UI, only in a running VM's state, or only in someone's memory. If it's not in git, it doesn't exist.

**Idempotent by default.** Every playbook is safe to re-run against a live host at any time. `state: present` over shell scripts. `creates:` guards on one-shot operations. Roles self-detect whether work is needed before doing it.

**Secrets never in plaintext.** ansible-vault encrypts all credentials before they touch disk or git. The vault password file is the only secret outside version control, and it lives only on the control node.

**Data separated from OS.** Every service that writes persistent data gets a dedicated disk. This makes VM snapshots clean (OS disk only), disk expansion non-disruptive, and VM rebuilds fast — clone template, reattach data disk, run role.

**No snowflake hosts.** Every VM is fully reproducible: golden template + Ansible role. If a host is lost, recovery is: clone template, run role, done. No manual steps, no institutional memory required.

**Monitoring is not optional.** node_exporter is deployed to every Linux VM at provisioning time, not retroactively. You cannot operate infrastructure you cannot observe.

**Blast radius is bounded.** Network automation is in a separate repository. Windows automation uses scoped service accounts. Ansible vault passwords are per-environment. A mistake in one domain cannot cascade into another.

**Pipelines over ad-hoc.** Production changes run through Jenkins pipelines with audit trails. Direct ansible-playbook invocations are for development and break-glass scenarios only.
