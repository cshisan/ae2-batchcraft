---
navigation:
  parent: index.md
  title: Breaking Unit Port
  icon: pp2mp_unit_port_break
  position: 120
item_ids:
- ae2_batchcraft:pp2mp_unit_port_break
---

# Breaking Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_break" scale="3" />

Breaks the block directly ahead during a unit job, or picks up fluid at that position.<br />&#160;<br />

## Usage

**Choose a target:** [Bind a unit manager](manager.md) and point the port at the position to process.<br />&#160;<br />

**Drop pickup:** Configure it on the manager's Breaking page. Enabled pickup returns drops from the broken block to the provider; disabled pickup leaves them in the world.<br />&#160;<br />

## Notes

**A job is required:** The unit must have an operational job. Returned resources count toward completion only when they meet the [product requirements](../configuration.md).<br />&#160;<br />

**Pickup scope:** Only drops from the current breaking operation are collected. Use a [Collection port](collect.md) or another device for nearby items.<br />&#160;<br />

**Pickup disabled:** Arrange separate collection so recipe products can still return.
