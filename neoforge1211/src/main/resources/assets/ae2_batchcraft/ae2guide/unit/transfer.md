---
navigation:
  parent: index.md
  title: Transfer Unit Port
  icon: pp2mp_unit_port_transfer
  position: 60
item_ids:
- ae2_batchcraft:pp2mp_unit_port_transfer
---

# Transfer Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_transfer" scale="3" />

Sends ingredients using the Normal output method into an adjacent machine's inventory or tank.<br />&#160;<br />

## Usage

**Connect the machine:** [Bind a unit manager](manager.md), then point the port at a machine face that allows insertion.<br />&#160;<br />

**Filters and priority:** Mark allowed materials. Set priority when several ports can accept the same resource.<br />&#160;<br />

**Feeding mode:** Supports Normal, Slot Exclusive, Single Item and Same Type; see the [unit manager](manager.md). Configure centrally, or disable port synchronization for local settings.<br />&#160;<br />

## Notes

**Distinct resources:** In Same Type mode, cobblestone and oak logs cannot share a port within one job. The same resource may use that port from multiple ingredient slots.<br />&#160;<br />

**Destination capacity:** The machine's insertion restrictions still apply. Feeding modes do not bypass capacity or resource limits.<br />&#160;<br />

**Product returns:** This port only delivers materials. Add Return, Extraction or other product retrieval ports.
