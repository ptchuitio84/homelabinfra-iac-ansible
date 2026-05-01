---
paths:
  - "jenkins/**"
  - "jenkins/**/*.groovy"
---

# Jenkins Pipeline Rules

- Parser: `JsonSlurperClassic` not `JsonSlurper` — LazyMap is not serializable across pipeline stages
- Inventory flag for ad-hoc IP targets: `-i ${VM_IP},` (trailing comma creates in-memory single-host inventory)
- SSH wait loop: use `while !` not `until` — `until` is unreliable in Groovy `sh` blocks
- SSH flags for new VMs: `-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes -o ConnectTimeout=5`
- Failure handling: `returnStatus: true` + `currentBuild.result = 'UNSTABLE'` for partial failures — lets full run complete
- Outer timeout must exceed approval gate + all stage runtimes (network enforce pipelines: 26h)
- Agent for Ansible work: `ans001` | Agent for Terraform work: `tfm001`
