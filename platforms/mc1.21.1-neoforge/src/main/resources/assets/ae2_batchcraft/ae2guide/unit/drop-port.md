---
navigation:
  parent: unit/index.md
  title: Drop Port
  icon: pattern_p2p_unit_port_drop
  position: 30
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_drop
---

# Unit Port (Drop)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_drop" />

Releases item materials encoded with the **Drop** output form as item entities in the block space in front of the port.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Active task | Required |
| Resources | Items only |
| Destination | Front block space |

The task is rejected if the encoded resource cannot use the Drop form or no compatible Drop Port is available. Keep the front space loaded and unobstructed, and provide a suitable machine or collection system for the item entities.

Dropping a material counts as dispatching that material; the Unit does not wait for a machine to pick the entity up.
