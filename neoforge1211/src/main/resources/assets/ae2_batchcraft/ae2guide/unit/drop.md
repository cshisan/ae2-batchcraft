---
navigation:
  parent: index.md
  title: Drop Unit Port
  icon: pp2mp_unit_port_drop
  position: 70
item_ids:
- ae2_batchcraft:pp2mp_unit_port_drop
---

# Drop Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_drop" scale="3" />

Releases materials in front of the port for recipes that process items dropped into the world.<br />&#160;<br />

## Usage

**Prepare the recipe:** [Bind a unit manager](manager.md) and set the ingredient's output method to Drop in the [PP2MP pattern](../pattern.md).<br />&#160;<br />

**Materials and mode:** Use port filters and the manager's four [feeding modes](manager.md). Disable synchronization to configure the port independently.<br />&#160;<br />

## Notes

**Space:** The area ahead must be suitable for releasing the material. Items become dropped entities.<br />&#160;<br />

**Other resources:** Only resources that can be released into the world are supported. AE network storage support alone is insufficient.<br />&#160;<br />

**Product collection:** This port does not retrieve products. Add a [Collection port](collect.md) or another collection device.
