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

Sync is shown on the right of the shared General page title. The Manager always has local values. When enabled, input broadcasts overwrite its return, extraction interval and amount, single-slot, transfer, break, redstone, pulse, and Unit Energy Port distribution settings. When disabled, broadcasts are ignored and local values remain in use; re-enabling Sync immediately applies the current input values. Unit General contains only the single-port single-slot setting.

The Transfer, Break, Redstone, and Energy Unit Port pages share one left-side button and cycle in that order.

Unit Port (Energy) controls only the second-stage distribution from this Manager's received FE to its bound Energy Ports. It remains effective while the Manager has no task.

The Manager's output-side ports can be prioritized independently. Output filter markers and the AE2 Inverter Card are configured from an output port's priority screen. The Inverter Card reverses the marker filter in the same way as AE2 filter upgrades.

Input-side ports have a separate return-filter screen with material markers and an optional Inverter Card. When recipe-viewer drag-and-drop is available, item and fluid ingredients can be dragged into marker slots on both input-side and output-side configuration screens.

The Manager-level **Single-port single-slot** setting is separate from the Transfer Port output mode. **All Enabled** reserves each output port for one encoded pattern slot, **All Disabled** allows slot sharing, and **Follow Port Configuration** uses each output port's own toggle. A port's toggle is editable only in the follow-port mode.

Transfer, Drop, and Place are output-side ports: they send encoded ingredients from the AE network to a machine or the world. Return, Collect, Break, and Extraction are input-side ports: they accept products from a machine or the world and are controlled by the active task's return and strict-mode rules. Strict mode also applies to normal Output returns.

## Reset

Reset clears only this Unit's active state and pending materials.

> Pending ingredients are permanently destroyed. Returned resources already stored in AE2's return path are not recreated by reset.

See [Frequency Binding](../getting-started/frequency.md) and the directly listed Unit Port pages below this page.
