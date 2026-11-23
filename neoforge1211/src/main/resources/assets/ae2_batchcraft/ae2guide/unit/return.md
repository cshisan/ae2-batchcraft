---
navigation:
  parent: index.md
  title: Return Unit Port
  icon: pp2mp_unit_port_return
  position: 90
item_ids:
- ae2_batchcraft:pp2mp_unit_port_return
---

# Return Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_return" scale="3" />

Accepts products pushed by a machine or pipe and returns them through the manager to the original pattern provider.<br />&#160;<br />

## Usage

**Connect the source:** [Bind a unit manager](manager.md) and connect the machine's or pipe's product output to the port's front.<br />&#160;<br />

**Filter products:** Use port filters alongside the manager's [return mode](../configuration.md).<br />&#160;<br />

## Notes

**A job is required:** The unit must have an operational job to return its products.<br />&#160;<br />

**No active extraction:** The source must push products in. Use an [Extraction port](extract.md) if the machine cannot output automatically.<br />&#160;<br />

**Blocked returns:** This port cannot replace an inventory for product storage. Keep the return route able to receive resources.
