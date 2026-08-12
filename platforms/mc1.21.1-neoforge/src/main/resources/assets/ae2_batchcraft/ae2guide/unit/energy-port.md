---
navigation:
  parent: unit/index.md
  title: Energy Port
  icon: pattern_p2p_unit_port_energy
  position: 100
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_energy
---

# Unit Port (Energy)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_energy" />

Continuously receives FE from Pattern P2P Energy Tunnels on the same AE grid and forwards it to the device against its front face.

| Property | Value |
| --- | --- |
| Unit identity | Required |
| Active crafting task | **Not required** |
| Resource | FE |
| Destination | Adjacent device on the port's front face |
| Configuration source | Bound Manager's energy distribution mode |

The port participates whenever it is bound to a valid Manager and the adjacent device can receive FE. The Manager may be idle and may have no active processing task; power delivery continues.

The Manager groups Energy Ports by Unit identity and supplies their distribution mode. **Even** shares available power across demand. **Round robin** rotates receiver priority when supply is limited. This setting is synchronized across the current AE grid.

The port does not draw FE from AE2's internal energy service. It only forwards FE supplied by a [Pattern P2P Energy Tunnel](../pattern-p2p/energy.md).
