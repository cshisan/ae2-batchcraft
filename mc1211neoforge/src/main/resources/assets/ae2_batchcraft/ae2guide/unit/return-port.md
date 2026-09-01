---
navigation:
  parent: unit/index.md
  title: Return Port
  icon: pattern_p2p_unit_port_return
  position: 50
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_return
---

# Unit Port (Return)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_return" />

Exposes an insertion-only item and fluid return capability to the device or pipe against its front face.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Operational Unit task | Required |
| Resources | Items and fluids |
| Transfer direction | Adjacent device pushes into the port |

The Return Port never actively pulls. Returned resources pass through the Manager's active return mode and then enter the Pattern Provider return path. Strict mode rejects types not declared by the active pattern.

Use an [Extraction Port](extraction-port.md) when the machine cannot push its products.
