# Lessons Learned

Living document. Each entry captures an issue that stopped progress, the investigation path, the root cause, and the fix. Add an entry any time a problem takes more than one session to resolve.

---

## LL-001 — Cisco IOS 12.2 SSH Authentication Failure via Ansible network_cli

**Date:** 2026-04-06 / 2026-04-07
**Severity:** High — blocked Jenkins network backup pipeline for ~2 days
**Affected:** Jenkins pipeline `nnt-jkn-net-backup`, Cisco Catalyst 3750G (swplnet251)

---

### Symptom

Jenkins pipeline failing since build #43. Last successful run: build #42 at 10:48 PM April 6.

```
fatal: [swplnet251.nnt.com]: FAILED! => {
  "msg": "Failed to authenticate: Authentication failed."
}
```

Manual SSH from ans001 to the switch worked fine. Vault decrypted correctly. Python paramiko direct test returned "Connected OK".

---

### Investigation Path (what we tried and why)

| Attempt | Hypothesis | Result |
|---|---|---|
| Check vault decryption | Password not decrypting | Vault OK |
| Check switch lockout (`show login`, `show ssh`) | Switch blocking IP | No lockout, sessions clean |
| Kill ansible-connection daemon | Stale daemon from old paramiko | No daemon running |
| Clear `/root/.ansible/pc/` socket files | Stale socket reused | Sockets empty |
| Check DNS resolution of `swplnet251.nnt.com` | Wrong host | Resolves correctly to 10.100.100.251 |
| Downgrade paramiko 4.0.0 → 2.12.0 | 4.0.0 dropped legacy KEX | Error message changed (progress) but still failing |
| Set `ANSIBLE_PARAMIKO_USE_RSA_SHA2_ALGORITHMS=false` | rsa-sha2 negotiation breaking IOS 12.2 | No effect |
| Set `ansible_paramiko_use_rsa_sha2_algorithms: false` in group_vars | Same theory | No effect |
| Set `ansible_paramiko_look_for_keys: false` in group_vars | Ed25519 keys exhausting auth retries | No effect (host vars not forwarded to inner plugin) |
| Add `look_for_keys = False` to `[paramiko_connection]` in ansible.cfg | Same theory, different delivery | **FIXED** |

---

### Root Cause (two-part)

**Part 1 — What triggered the break:**
Running `pip install ansible-pylibssh` on April 6 caused pip to upgrade paramiko from the working version to **4.0.0**. Paramiko 4.0.0 dropped legacy KEX algorithm support (`diffie-hellman-group14-sha1`) that IOS 12.2 requires. This caused the immediate "transport shut down or saw EOF" error.

**Part 2 — Why downgrading to 2.12.0 didn't fix it:**
Downgrading paramiko fixed the transport/KEX issue but exposed a **pre-existing config gap**:

- `ans001` has 3 ed25519 private keys in `/root/.ssh/`: `id_ed25519`, `id_ed25519_github`, `id_ed25519_netconfigs`
- Ansible's paramiko plugin defaults `look_for_keys = True`
- With `look_for_keys = True`, paramiko tries all private keys in `~/.ssh/` **before** attempting password authentication
- Cisco IOS 12.2 has a maximum of 3 SSH authentication attempts per session
- All 3 ed25519 key attempts fail (IOS 12.2 doesn't support ed25519 and the keys aren't authorized on the switch)
- Auth retry budget exhausted — the password is **never sent**
- Switch returns `Authentication Failed` → paramiko raises `AuthenticationException`

This worked before because the original paramiko version either had different `look_for_keys` behavior or the key exhaustion wasn't being triggered consistently. The 4.0.0 upgrade + downgrade cycle brought it to the surface.

**Why host vars and env vars didn't work:**
`ansible_paramiko_*` host vars and `ANSIBLE_PARAMIKO_*` env vars are NOT reliably forwarded to the inner paramiko plugin that runs inside the `ansible-connection` daemon. The daemon is a background subprocess that reads `ansible.cfg` on startup but does not inherit the shell's env vars or host var options in the same way the foreground process does.

---

### Fix

**`ansible.cfg`** — added a `[paramiko_connection]` section:

```ini
[paramiko_connection]
look_for_keys = False
```

This is read by the `ansible-connection` daemon on startup regardless of how it was spawned.

**`inventory/group_vars/cisco_switches/vars.yml`** — added for documentation/defense-in-depth:

```yaml
ansible_network_cli_ssh_type: paramiko
ansible_paramiko_use_rsa_sha2_algorithms: false
ansible_paramiko_look_for_keys: false
```

Note: the vars.yml entries are redundant (the daemon doesn't reliably apply them) but serve as documentation of intent for future maintainers.

---

### Key Takeaways

1. **`pip install <anything>` can silently upgrade paramiko.** Pin it or check after any pip operation: `pip show paramiko`.

2. **When Ansible uses `network_cli` + paramiko, `ansible_paramiko_*` host vars and shell env vars do NOT reliably reach the inner plugin.** Only `ansible.cfg` `[paramiko_connection]` settings are guaranteed to apply.

3. **Private keys in `~/.ssh/` are tried by paramiko before password auth** when `look_for_keys = True` (default). On devices with strict auth retry limits (Cisco IOS, network gear in general), this silently exhausts the retry budget.

4. **"Authentication failed" ≠ wrong password.** It can also mean auth methods were exhausted before the password was ever attempted.

5. **Direct Python paramiko test is not equivalent to Ansible's paramiko usage.** Direct test had `look_for_keys=False` explicitly — that masked the real issue for hours.

6. **The ansible-connection daemon is a separate process.** Killing it and clearing `/root/.ansible/pc/` socket files is the correct reset procedure. Changes to Python packages require a fresh daemon.

---

### Related Commits

| Commit | Description |
|---|---|
| `ffb46da` | Force system SSH for Cisco group — paramiko ignores legacy cipher args |
| `3ae2010` | Use openssh for network_cli — invalid in netcommon 2.15.x |
| `d8f8ac4` | Revert network_cli_ssh_type — openssh not valid |
| `5fa869b` | Add RequiredRSASize=512 for Cisco IOS 12.2 short RSA host key |
| `93200fd` | Disable rsa-sha2 algorithms for Cisco IOS 12.2 paramiko compatibility |
| `72295ab` | Fix Cisco auth: disable look_for_keys to stop ed25519 key exhaustion |
| `983d74d` | Add [paramiko_connection] section: disable look_for_keys **(THE FIX)** |

---

*Add new entries below using the same format. Include date, symptom, investigation path, root cause, and fix.*
