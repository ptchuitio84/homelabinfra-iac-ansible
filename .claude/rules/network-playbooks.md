---
paths:
  - "playbooks/network/**"
  - "roles/*/tasks/network*"
---

# Network Playbook Rules

- Cisco IOS tasks must delegate to ans001 or run from ans001 — Mac OpenSSH KEX incompatible with IOS 12.2
- `ios_config` tasks: always set `match: none` for baseline push tasks — IOS reformats lines, text compare always shows drift
- Arista EOS: prefer `arista.eos.eos_config` with eAPI transport over SSH
- Meraki: use numeric org ID (1679044) not the URL slug — query `/api/v1/organizations` if unsure
- Network backup pipeline saves to: `network/backups/<device>_<timestamp>.cfg`
- Approval gate timeout on enforce pipelines: 26 hours (dry-run ~1h + 24h approval window + enforce ~1h)
