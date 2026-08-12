---
navigation:
  parent: unit/index.md
  title: Unit Manager
  icon: pattern_p2p_unit_manager
  position: 10
item_ids:
- ae2_batchcraft:pattern_p2p_unit_manager
- ae2_batchcraft:white_pattern_p2p_unit_manager
- ae2_batchcraft:light_gray_pattern_p2p_unit_manager
- ae2_batchcraft:gray_pattern_p2p_unit_manager
- ae2_batchcraft:black_pattern_p2p_unit_manager
- ae2_batchcraft:lime_pattern_p2p_unit_manager
- ae2_batchcraft:yellow_pattern_p2p_unit_manager
- ae2_batchcraft:orange_pattern_p2p_unit_manager
- ae2_batchcraft:brown_pattern_p2p_unit_manager
- ae2_batchcraft:red_pattern_p2p_unit_manager
- ae2_batchcraft:pink_pattern_p2p_unit_manager
- ae2_batchcraft:magenta_pattern_p2p_unit_manager
- ae2_batchcraft:purple_pattern_p2p_unit_manager
- ae2_batchcraft:blue_pattern_p2p_unit_manager
- ae2_batchcraft:light_blue_pattern_p2p_unit_manager
- ae2_batchcraft:cyan_pattern_p2p_unit_manager
- ae2_batchcraft:green_pattern_p2p_unit_manager
---

# Pattern P2P Unit Manager

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_manager" />

Joins the input's endpoint pool and coordinates one Unit processing job at a time.

| Property | Value |
| --- | --- |
| AE channels | `0` |
| Pattern P2P frequency | Required for task distribution |
| Unit identity | Required by every owned port |
| Concurrent jobs | `1` |
| Color variants | Fluix plus `16` cable colors |

## Installation and Binding

Install the Manager as a cable center on the AE subnet. The target block position must not already contain a center cable, cable part, or facade. Load the input frequency into it, then save the Manager identity to a Memory Card and load that identity into every port.

Color follows AE cable connectivity rules. It changes which cable colors can connect; it does not define port ownership.

## Task Admission

Before accepting a task, the Manager verifies that every encoded material has a compatible bound port capable of accepting the complete resource. **Normal**, **Drop**, and **Place** select Transfer, Drop, and Place Ports respectively.

After acceptance, pending materials remain tracked until dispatched. The primary product and pending ingredients are completed independently, so an early returned product cannot discard unsent material.

## Configuration

With synchronization enabled, the Manager uses the input's return, extraction interval and amount, break recovery, and redstone settings. Disable synchronization to use its local configuration.

Energy distribution mode is synchronized across the current AE grid. It affects the Manager's Energy Ports even while this Manager has no task.

## Reset

Reset clears only this Unit's active state and pending materials.

> Pending ingredients are permanently destroyed. Returned resources already stored in AE2's return path are not recreated by reset.

See [Frequency Binding](../getting-started/frequency.md) and the directly listed Unit Port pages below this page.
