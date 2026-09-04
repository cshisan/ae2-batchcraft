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

The task allocation button in the right toolbar controls endpoint selection. **Round Robin** starts at its saved cursor and advances it after a successful selection. **Random** chooses a random starting endpoint, then continues trying the other endpoints when that attempt fails. **Priority** starts from the first endpoint on every allocation and only continues to later endpoints when an earlier one cannot accept the task.

Offline, unloaded, busy, or incompatible endpoints are unavailable. A selected endpoint receives the full quantities of that processing push.

## General Configuration

The input provides defaults for:

- Product return mode.
- Round Robin, Random, or Priority task allocation.
- Whether normal outputs may actively extract products.
- Extraction interval and amount for outputs and Unit Extraction Ports.
- Unit break recovery and redstone behavior.
- Unit single-port single-slot, transfer, and Unit Energy Port distribution settings.

These settings are synchronized dynamically while a task is active. The Unit Manager and its bound ports apply later changes without requiring a new task.

Normal outputs and Unit Managers always keep local values. While synchronization is enabled, input broadcasts overwrite those values; when disabled, broadcasts are ignored and the local values remain in use. Re-enabling synchronization applies the current input values immediately.

## Reset Task State

Reset from the input affects loaded normal outputs and Unit Managers on its current frequency and AE grid.

> Reset permanently destroys ingredients still waiting inside those endpoints. Unloaded endpoints cannot be reset remotely.

See [Pattern P2P Output](output.md), [Unit Manager](../unit/manager.md), and [Product Return and Extraction](../product-return/index.md).
