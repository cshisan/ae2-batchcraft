---
navigation:
  parent: unit/index.md
  title: Place Port
  icon: pattern_p2p_unit_port_place
  position: 40
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_place
---

# Unit Port (Place)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_place" />

Places supported block items or fluids encoded with the **Place** output form into the world in front of the port.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Active task | Required |
| Resources | Placeable blocks and fluids |
| Destination | Front block space |

The Manager rejects a task when the resource does not support world placement or no compatible Place Port can accept it. If the target position is temporarily occupied or otherwise invalid, the material remains pending and is retried.

World placement uses the owning player's automation context. Protection mods or claim rules may therefore block it.
