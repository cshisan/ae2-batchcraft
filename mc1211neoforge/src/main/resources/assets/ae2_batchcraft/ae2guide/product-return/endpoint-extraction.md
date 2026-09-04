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

The switch therefore controls only whether normal Outputs have extraction capability. The interval and amount affect both endpoint types. New extraction also requires an active, runnable task for both normal Outputs and Unit Extraction Ports; Unblocked mode does not bypass this requirement.

The configured amount is the maximum resource-operation budget for one scheduled pass. It is not a promise that the whole amount moves in one tick. A small inventory may provide less, while a large amount may require later ticks.

During one tick, each extraction endpoint may complete at most `64` successful return-inventory rounds. All extraction endpoints on one AE grid share a limit of `256` successful rounds per tick. A round may move many units of one resource; these limits count successful rounds, not individual items or fluid units.

After a successful round enters the AE network, the return inventory can be reused during the same tick. If ME storage or the return inventory cannot accept another resource, extraction stops instead of pulling more. Anything already in the return inventory remains there and is retried. Endpoints deferred by the shared limit continue on a later tick.

Empty attempts add a gradual backoff of at most `20` ticks. Inventory changes and recovered return capacity can wake a waiting extractor early, while a successful extraction still respects the configured minimum interval.

Extraction may start before every input material has been dispatched. This is intentional: the task remains active until the pending-material queue is empty. If a resource was pulled but cannot immediately enter the return path, it is held in a recovery queue and retried rather than silently discarded.

The recovery queue may continue draining after the task has ended. It contains only resources already pulled for return and never permits a new extraction after completion.

Use direct return when the adjacent inventory already sends products reliably. Enable endpoint extraction when products must be pulled or when a configured pull rate is useful.
