---
navigation:
  parent: index.md
  title: Pattern P2P Tunnel (Input)
  icon: pattern_p2p_tunnel_input
  position: 20
item_ids:
- ae2_batchcraft:pattern_p2p_tunnel_input
---

# Pattern P2P Tunnel (Input)

<RecipeFor id="ae2_batchcraft:pattern_p2p_tunnel_input" />

Receives processing jobs from the AE2 Pattern Provider in front of it and distributes them among normal Outputs and Unit Managers on the same frequency.

| Property | Value |
| --- | --- |
| AE channels | `1` |
| Frequency | Required; `0000` is disabled |
| Adjacent block | Pattern Provider output face |
| Dispatch mode | Full Dispatch or Batch Distribution |
| Task allocation | Round Robin, Random, or Priority |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/input-provider.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Task Distribution

In **Full Dispatch**, one selected endpoint receives the complete processing push. Offline, unloaded, busy, or incompatible endpoints are unavailable.

In **Batch Distribution**, the input requests only the next planned share from the adjacent AE2 Pattern Provider and distributes integer multiples of the configured smallest share. Unsent materials remain in the Pattern Provider until endpoint capacity becomes available. See [Batch Distribution](batch-distribution.md) before enabling this mode.

The task allocation button in the right toolbar controls endpoint selection. **Round Robin** rotates through the stable endpoint order. **Random** independently selects one currently available endpoint for each allocation and may select the same endpoint repeatedly. **Priority** starts from the first endpoint on every allocation and only continues to later endpoints when an earlier one cannot accept the task.

## General Configuration

The input provides defaults for:

- Product return mode.
- Full Dispatch or Batch Distribution.
- Round Robin, Random, or Priority task allocation.
- Whether normal outputs may actively extract products.
- Extraction interval and amount for outputs and Unit Extraction Ports.
- Unit break recovery and redstone behavior.

These settings are synchronized dynamically while a task is active. The Unit Manager and its bound ports apply later changes without requiring a new task.

Normal outputs and Unit Managers follow the relevant settings while synchronization is enabled. Their local settings become effective when synchronization is disabled.

## Reset Task State

Reset from the input affects loaded normal outputs and Unit Managers on its current frequency and AE grid.

> Reset permanently destroys ingredients still waiting inside those endpoints. Unloaded endpoints cannot be reset remotely.

See [Batch Distribution](batch-distribution.md), [Pattern P2P Output](output.md), [Unit Manager](../unit/manager.md), and [Product Return and Extraction](../product-return/index.md).
