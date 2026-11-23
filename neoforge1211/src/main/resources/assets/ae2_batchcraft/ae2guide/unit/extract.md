---
navigation:
  parent: index.md
  title: Extraction Unit Port
  icon: pp2mp_unit_port_extract
  position: 100
item_ids:
- ae2_batchcraft:pp2mp_unit_port_extract
---

# Extraction Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_extract" scale="3" />

Actively retrieves job products from an adjacent machine or inventory, useful when the machine cannot output them automatically.<br />&#160;<br />

## Usage

**Connect the machine:** [Bind a unit manager](manager.md) and point the port at a face that permits extraction.<br />&#160;<br />

**Filters and rate:** Filter products and set interval and amount in the manager's common settings. The minimum interval is `1 tick`.<br />&#160;<br />

## Notes

**A job is required:** The unit needs an operational job, and resources must satisfy the manager's [return mode](../configuration.md).<br />&#160;<br />

**Extraction amount:** The setting is a per-cycle limit. Available stock, machine restrictions and return capacity may reduce the actual amount.<br />&#160;<br />

**Separate switch:** The ordinary output's Product Extraction switch does not control this port.
