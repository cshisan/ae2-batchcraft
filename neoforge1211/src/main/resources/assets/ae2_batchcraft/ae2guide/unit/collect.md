---
navigation:
  parent: index.md
  title: Collection Unit Port
  icon: pp2mp_unit_port_collect
  position: 110
item_ids:
- ae2_batchcraft:pp2mp_unit_port_collect
---

# Collection Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_collect" scale="3" />

Collects dropped items and collectible world fluids in the block space directly ahead, returning them through the manager to the provider.<br />&#160;<br />

## Usage

**Choose the position:** [Bind a unit manager](manager.md) and point the port at the space where products appear.<br />&#160;<br />

**Filters and interval:** Mark allowed resources. The interval uses the manager's extraction settings, with a minimum of `1 tick`.<br />&#160;<br />

## Notes

**A job is required:** The unit needs an operational job. Collected resources must satisfy its [return mode](../configuration.md).<br />&#160;<br />

**Collection area:** Only the block space ahead is covered. This port neither extracts from inventories nor breaks solid blocks.<br />&#160;<br />

**Collection amount:** A dropped stack may be collected at once. The manager's amount setting is not a strict individual-item limit for each collection cycle.<br />&#160;<br />

**Mixed drops:** Drops that cannot be accepted are skipped, while other eligible products are still tried. Return destination capacity limits what can actually be collected.
