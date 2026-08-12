---
navigation:
  parent: unit/index.md
  title: Collect Port
  icon: pattern_p2p_unit_port_collect
  position: 70
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_collect
---

# Unit Port (Collect)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_collect" />

Collects dropped items from the block space in front of it or a source fluid in the front block.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Operational Unit task | Required |
| Dropped-item collection | Item entities in the front block space |
| Fluid collection | Source fluid in the front block |

Collected resources pass through the Manager's active return rule. An item entity is reduced, or a source fluid removed, only when the return path can accept it.

Choose this port when a process drops its result into the world or produces a source fluid. Use a Return Port for push-only automation.
