---
navigation:
  parent: troubleshooting/index.md
  title: Task State and Reset
  position: 30
---

# Task State and Reset

Reset is a recovery operation for a task that can no longer complete. It is not a normal way to cancel crafting.

| Reset location | Scope |
| --- | --- |
| Normal Output | That Output's active task |
| Unit Manager | That Unit's active task |
| Pattern P2P Input | Every loaded Output and Manager on the Input's frequency and current AE subnet |

Reset permanently destroys materials still waiting in the affected endpoint's pending-material queue, so every reset requires confirmation. Verify the machine received all required ingredients before proceeding. Unloaded endpoints cannot be reset remotely and will retain their own state until loaded.

Before resetting, check the return route, ME capacity, machine output face, and whether one material is still waiting for a blocked destination. A task may already have returned its product yet remain active because pending materials have not all been dispatched; that state is intentional.
