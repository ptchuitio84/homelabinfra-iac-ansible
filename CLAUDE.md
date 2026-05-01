# homelabinfra-iac-ansible

Ansible configuration management and CI/CD pipelines for the NNT homelab.
Terraform provisions VMs. Ansible configures what runs inside them.

## Control Node
- Host: hmvlapans001 — 10.10.1.31
- Repo path: /opt/homelabinfra-iac-ansible
- Vault password: /root/.ansible/vault_pass.txt
- ansible-core: 2.15.x pinned — OL9 ships Python 3.9. Do not upgrade ansible-core without upgrading Python first.

## Run Commands
```bash
ansible-playbook playbooks/<path>.yml --vault-password-file ~/.ansible/vault_pass.txt
ansible-playbook playbooks/<path>.yml --vault-password-file ~/.ansible/vault_pass.txt --limit <host>
ansible-playbook playbooks/<path>.yml --vault-password-file ~/.ansible/vault_pass.txt -i <IP>,
```

## Structure
```
inventory/          # hosts + group_vars (vars.yml + vault.yml per group)
playbooks/
  infra/            # VM provisioning, destroy, DNS, NetBox
  linux/            # OS config, patching, k8s setup, domain join
  network/          # switch baseline enforcement, backup
  windows/          # DNS sync, AD tasks
roles/              # Reusable roles (one per service/function)
jenkins/            # Groovy pipeline definitions
```

## Secrets
- All secrets in `group_vars/*/vault.yml` (ansible-vault encrypted)
- Never in `vars.yml`, never in git plaintext
- `community.hashi_vault` lookup broken on ansible-core 2.15 — use `uri` module against Vault HTTP API instead

## Key Gotchas
- Never hardcode disk device names (sda/sdb). VMware ordering is non-deterministic. Detect by size using `lsblk` or `pvs`.
- fstab must use UUID, never device name — device names shift after kernel updates
- `ansible.builtin.package` over `ansible.builtin.dnf` for roles that run on mixed OS versions (OL7 + OL9)
- OL9 firewalld blocks all non-explicitly-opened ports — every exporter role needs a firewalld task
- Cisco SSH must run from ans001 — Mac OpenSSH too new for IOS 12.2 KEX
- `ios_config` idempotency: use `match: none` — IOS reformats config lines on write, text compare always shows drift

## Domain — nanonetech.com
- Domain join role: `roles/ad_join` — idempotent, vault-backed credentials
- Standalone playbook: `playbooks/linux/join_domain.yml`
- Unjoin playbook: `playbooks/linux/leave_domain.yml`
- AD join user/password: `vault_ad_join_user` / `vault_ad_join_password` in vault

## Jenkins Pipelines (key ones)
| Pipeline | File | Agent |
|----------|------|-------|
| nnt-jkn-provision-vm | jenkins/infra/nnt-jkn-provision-vm.groovy | ans001 |
| nnt-jkn-terraform-apply | jenkins/infra/nnt-jkn-terraform-apply.groovy | tfm001 |
| nnt-jkn-lin-patch | jenkins/linux/nnt-jkn-lin-patch.groovy | ans001 |
| nnt-jkn-net-backup | jenkins/network/nnt-jkn-net-backup.groovy | ans001 |
| nnt-jkn-win-dns-sync | jenkins/windows/nnt-jkn-win-dns-sync.groovy | ans001 |
