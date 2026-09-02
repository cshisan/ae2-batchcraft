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

Receives complete processing jobs from the Pattern Provider in front of it and distributes them among normal outputs and Unit Managers on the same frequency.

| Property | Value |
| --- | --- |
| AE channels | `1` |
| Frequency | Required; `0000` is disabled |
| Adjacent block | Pattern Provider output face |
| Job splitting | Between complete jobs only |
| Task allocation | Round Robin, Random, or Priority |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/input-provider.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Task Distribution

The task allocation button in the right toolbar controls endpoint selection. **Round Robin** rotates through the stable endpoint order. **Random** independently selects one currently available endpoint for each allocation and may select the same endpoint repeatedly. **Priority** starts from the first endpoint on every allocation and only continues to later endpoints when an earlier one cannot accept the task.

Offline, unloaded, busy, or incompatible endpoints are unavailable. A selected endpoint receives the full quantities of that processing push.

## General Configuration

The input provides defaults for:

- Product return mode.
- Round Robin, Random, or Priority task allocation.
- Whether normal outputs may actively extract products.
- Extraction interval and amount for outputs and Unit Extraction Ports.
- Unit break recovery and redstone behavior.

These settings are synchronized dynamically while a task is active. The Unit Manager and its bound ports apply later changes without requiring a new task.

Normal outputs and Unit Managers follow the relevant settings while synchronization is enabled. Their local settings become effective when synchronization is disabled.

## Reset Task State

Reset from the input affects loaded normal outputs and Unit Managers on its current frequency and AE grid.

> Reset permanently destroys ingredients still waiting inside those endpoints. Unloaded endpoints cannot be reset remotely.

See [Pattern P2P Output](output.md), [Unit Manager](../unit/manager.md), and [Product Return and Extraction](../product-return/index.md).
