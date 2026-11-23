---
navigation:
  parent: index.md
  title: Placement Unit Port
  icon: pp2mp_unit_port_place
  position: 80
item_ids:
- ae2_batchcraft:pp2mp_unit_port_place
---

# Placement Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_place" scale="3" />

Places assigned blocks or fluids directly ahead for recipes performed in the world.<br />&#160;<br />

## Usage

**Prepare the recipe:** [Bind a unit manager](manager.md) and set the ingredient's output method to Place in the [PP2MP pattern](../pattern.md).<br />&#160;<br />

**Materials and mode:** Filter the allowed materials and choose from the four [feeding modes](manager.md). Disable synchronization for independent settings.<br />&#160;<br />

## Notes

**Placement position:** Point the port at the target position, which must allow the chosen material to be placed.<br />&#160;<br />

**Ordinary items:** Items that cannot be placed as blocks are unsuitable. Use a [Drop port](drop.md) to release them.<br />&#160;<br />

**Retrieve products:** This port only places materials. Arrange breaking, collection or another product retrieval method for the recipe.
