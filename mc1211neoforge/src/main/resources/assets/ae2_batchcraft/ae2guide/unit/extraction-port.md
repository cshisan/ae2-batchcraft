---
navigation:
  parent: unit/index.md
  title: Extraction Port
  icon: pattern_p2p_unit_port_extract
  position: 60
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_extract
---

# Unit Port (Extraction)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_extract" />

Actively pulls products from the machine against its front face while its Unit task is operational.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Operational Unit task | Required |
| Resources | AE-compatible items and fluids |
| Input extraction switch | Ignored by this port |
| Applied settings | Extraction interval and amount |

The input's extraction switch controls only normal outputs. The interval and amount below that switch still apply to synchronized Extraction Ports; an unsynchronized Manager uses its local values.

Extraction may begin before all ingredients have finished dispatching. The Unit keeps pending ingredients separately and cannot complete until they are sent. Empty attempts use a gradual idle backoff capped at `20` ticks. Capability changes, return capacity, and recovery progress can wake extraction early without shortening the configured minimum interval after a successful extraction.

If a pulled resource cannot immediately enter the return path, it is kept in the recovery queue and retried; it is not silently deleted.

See [Output Extraction](../product-return/endpoint-extraction.md).
