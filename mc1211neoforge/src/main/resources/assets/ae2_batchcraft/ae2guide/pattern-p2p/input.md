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

The task allocation button in the right toolbar controls endpoint selection. **Round Robin** starts at its saved cursor and advances it after a successful selection. **Random** chooses a random starting endpoint, then continues trying the other endpoints when that attempt fails. **Priority** starts from the first endpoint on every allocation and only continues to later endpoints when an earlier one cannot accept the task.

## General Configuration

The page-group button at the top of the right toolbar cycles the left page set between Output Pages and Unit Pages. General is one shared page in both groups; switching groups neither copies nor resets its data. The four Unit Port pages share one left-side button and cycle in Transfer, Break, Redstone, and Energy order.

The input provides defaults for:

- Product return mode.
- Full Dispatch or Batch Distribution.
- Round Robin, Random, or Priority task allocation.
- Whether normal outputs may actively extract products.
- Extraction interval and amount for outputs and Unit Extraction Ports.
- Unit break recovery and redstone behavior.
- Single-port single-slot, transfer mode, and second-stage Unit Energy Port distribution.

The standard-output extraction Enabled/Disabled button is on Output General, while extraction interval and amount remain on the shared General page. Other Unit settings are separated into Unit General and per-port pages.

These settings are broadcast dynamically while a task is active. Outputs and Unit Managers apply later changes without requiring a new task when Sync is enabled; otherwise they keep using local values.

Normal outputs and Unit Managers always keep local values. While synchronization is enabled, input broadcasts overwrite those values; when disabled, broadcasts are ignored. Re-enabling synchronization immediately applies the current input values.

## Reset Task State

Reset from the input affects loaded normal outputs and Unit Managers on its current frequency and AE grid.

> Reset permanently destroys ingredients still waiting inside those endpoints. Unloaded endpoints cannot be reset remotely.

See [Batch Distribution](batch-distribution.md), [Pattern P2P Output](output.md), [Unit Manager](../unit/manager.md), and [Product Return and Extraction](../product-return/index.md).
