---
navigation:
  parent: unit/index.md
  title: Transfer Port
  icon: pattern_p2p_unit_port_transfer
  position: 20
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_transfer
---

# Unit Port (Transfer)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_transfer" />

Inserts materials encoded with the **Normal** output form into the inventory or tank against its front face.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Active task | Required for actual insertion |
| Resources | AE-compatible items and fluids |
| Direction | Port front face |

The Manager probes the port before accepting a task. During dispatch, partial insertion leaves the remainder pending and the Unit retries later. A simulation can succeed before task activation, but world mutation is blocked until the Manager's task is operational.

Use multiple Transfer Ports when a machine needs separate inventories or tanks. The Manager chooses the first compatible bound port for each material.

See [Material Output Directions](../troubleshooting/material-directions.md).
