---
navigation:
  parent: index.md
  title: ME IO Port
  icon: io_port
  position: 175
item_ids:
- ae2_batchcraft:io_port
---

# ME IO Port

<ItemImage id="ae2_batchcraft:io_port" scale="3" />

Fills or empties storage cells on an ME network, then moves completed cells to output slots or adjacent inventories.<br />&#160;<br />

## Usage

**Process cells:** Provides six input slots and six output slots, with filling, emptying and completion settings.<br />&#160;<br />

**Choose a transfer mode:** Cell Transfer on the left controls where completed cells go. The four modes are listed below.<br />&#160;<br />

**Select external destinations:** Output Sides supports multiple faces. Click a selected face to deselect it, or use the cross to clear all faces.<br />&#160;<br />

## Transfer Modes

**Transfer to Slots:** Moves cells only into the device's output slots.<br />&#160;<br />

**Transfer to External:** Moves cells only into adjacent inventories on selected faces.<br />&#160;<br />

**Prefer Slots:** Uses output slots first, then external inventories for cells that do not fit.<br />&#160;<br />

**Prefer External:** Uses external inventories first, then output slots for cells that do not fit.<br />&#160;<br />

## Notes

**Power and upgrades:** Requires power and a channel. Five shared upgrade slots allow up to five acceleration cards and one redstone card. A redstone card leaves room for at most four acceleration cards.<br />&#160;<br />

**External fallback:** Prefer Slots hides Output Sides. Select faces in an external mode first, then switch back to use them as fallback destinations. Switching modes keeps the selected faces.<br />&#160;<br />

**Full or unselected destinations:** Cells wait in input slots if no permitted destination can receive them. Transfer to External requires selected output faces.<br />&#160;<br />

**Automation:** All faces accept cells for processing. The four faces other than the relative top and bottom allow extraction from output slots. Cells already in output slots do not automatically move outside.
