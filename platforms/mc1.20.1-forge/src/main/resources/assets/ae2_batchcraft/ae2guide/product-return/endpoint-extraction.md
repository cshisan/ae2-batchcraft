---
navigation:
  parent: product-return/index.md
  title: Endpoint Extraction
  icon: pattern_p2p_tunnel_output
  position: 20
---

# Endpoint Extraction

Endpoint extraction lets BatchCraft pull products from the inventory or resource capability in front of an endpoint instead of waiting for the machine to push them.

| Input setting | Normal Output | Unit Extraction Port |
| --- | --- | --- |
| Product extraction switch | Enables or disables extraction | Ignored |
| Extraction interval | Applied | Applied through the Manager |
| Extraction amount | Applied | Applied through the Manager |

The switch therefore controls only whether normal Outputs have extraction capability. The interval and amount affect both endpoint types. A Unit Extraction Port still requires its Manager's task to be operational.

At each configured deadline, the endpoint performs up to the configured number of extraction operations. Empty attempts add a gradual backoff of at most `20` ticks. Inventory capability changes and recovered return capacity can wake a waiting extractor early, while a successful extraction still respects the configured minimum interval.

Extraction may start before every input material has been dispatched. This is intentional: the task remains active until the pending-material queue is empty. If a resource was pulled but cannot immediately enter the return path, it is held in a recovery queue and retried rather than silently discarded.

Use the machine's native push behavior when it is reliable. Enable endpoint extraction when a machine cannot push, has several incompatible output faces, or benefits from the configured pull rate.
