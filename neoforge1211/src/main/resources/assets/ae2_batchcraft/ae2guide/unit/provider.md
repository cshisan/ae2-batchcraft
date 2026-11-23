---
navigation:
  parent: index.md
  title: Supply Unit Port
  icon: pp2mp_unit_port_provider
  position: 140
item_ids:
- ae2_batchcraft:pp2mp_unit_port_provider
---

# Supply Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_provider" scale="3" />

Continuously sends resources from a resource intake to an adjacent machine, replenishing stock even without a crafting job.<br />&#160;<br />

## Usage

**Connect the destination:** [Bind a unit manager](manager.md) and face the target inventory. The resource intake and manager must share an AE network and frequency.<br />&#160;<br />

**Maintain stock:** Mark a resource and middle-click to set the desired destination stock. `0` means unlimited.<br />&#160;<br />

**Local filtering:** Follow the manager's supply filter or disable synchronization for local settings. An inverter card enables a blacklist.<br />&#160;<br />

## Notes

**Amount meaning:** Target stock is not the amount sent each time. Blacklists ignore target amounts; see [Supply Filtering](../provider_filter.md).<br />&#160;<br />

**Continuous supply:** No crafting job needs to start or finish. The destination must still permit insertion of the resource.
