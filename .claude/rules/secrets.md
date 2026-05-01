---
paths:
  - "**/vault.yml"
  - "**/vault.yaml"
---

# Vault File Rules

These files are ansible-vault encrypted. Never write plaintext secrets here.

- Encrypt with: `ansible-vault encrypt <file> --vault-password-file ~/.ansible/vault_pass.txt`
- Edit with: `ansible-vault edit <file> --vault-password-file ~/.ansible/vault_pass.txt`
- View with: `ansible-vault view <file> --vault-password-file ~/.ansible/vault_pass.txt`
- Variable names follow the pattern: `vault_<group>_<purpose>` (e.g., `vault_windows_admin_password`)
- Do not use `community.hashi_vault` lookup on ansible-core 2.15 — use `uri` module against Vault HTTP API
