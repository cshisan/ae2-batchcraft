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

The Energy Tunnel first distributes FE among normal-output groups and Unit groups. After a Unit group receives FE, its Manager performs a second-stage distribution among the bound Energy Ports. **Even** shares power across demand; **Round robin** rotates port priority when supply is limited. A Manager uses the input setting while Sync in the General page title is enabled and its local setting otherwise.

The port does not draw FE from AE2's internal energy service. It only forwards FE supplied by a [Pattern P2P Energy Tunnel](../pattern-p2p/energy.md).
