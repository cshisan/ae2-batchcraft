---
navigation:
  parent: unit/index.md
  title: Break Port
  icon: pattern_p2p_unit_port_break
  position: 80
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_break
---

# Unit Port (Break)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_break" />

Uses AE2 world collect strategies to break blocks or collect source fluids in front of the port during an operational Unit task.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Operational Unit task | Required |
| Targets | Breakable blocks and source fluids |
| Configuration | Recover broken items |

With **Recover broken items** enabled, accepted drops return through the Manager. With it disabled, item drops are spawned into the world in front of the port. Source fluids always return directly because they cannot be represented as world item drops.

The return rule and available return capacity are checked before a collect is committed. A rejected interaction leaves the task available for a later retry.
