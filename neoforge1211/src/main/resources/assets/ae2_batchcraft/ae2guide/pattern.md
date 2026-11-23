---
navigation:
  parent: index.md
  title: PP2MP Pattern
  icon: pp2mp_pattern
  position: 160
item_ids:
- ae2_batchcraft:pp2mp_pattern
---

# PP2MP Pattern

<ItemImage id="ae2_batchcraft:pp2mp_pattern" scale="3" />

A processing pattern with ingredient output methods, directions and batch quantities. It also supports a real output marker for fake crafting.<br />&#160;<br />

## Usage

**Encode a recipe:** Right-click air to open the editor, or right-click a connected AE block to also access network storage. Set ingredients and products, then click Encode.<br />&#160;<br />

**Ingredient settings:** `Ctrl-middle-click` an input slot to select Normal, Drop, Place or Fake Crafting, and an output direction where applicable.<br />&#160;<br />

**Batch settings:** `Ctrl-middle-click` the first product to set the [batch count](batch.md), then select batch distribution on the input.<br />&#160;<br />

**Real output:** A marker slot appears above Encode when a nonempty ingredient uses Fake Crafting. See [Fake Crafting Converter](fake_crafting_converter.md) for setup and completion rules.<br />&#160;<br />

## Notes

**Recipe type:** Only processing recipes are supported. Invalid ingredients, products or quantities produce an explanation instead of saving an invalid pattern.<br />&#160;<br />

**Empty slots:** Gaps between ingredients also support Normal, Drop and Place, but not Fake Crafting. They do not replace ingredients and can reserve ports in [Slot Exclusive mode](unit/manager.md).<br />&#160;<br />

**Directions and ports:** An ordinary output's direction selects a face of the same adjacent machine. Unit ports use their own facing. Drop and Place need their corresponding unit ports.<br />&#160;<br />

**Clear encoding:** Shift-right-click to restore a blank PP2MP pattern. Blank patterns stack; encoded patterns do not.<br />&#160;<br />

**Convert to AE2:** Craft one PP2MP pattern alone to obtain the corresponding blank or encoded AE2 pattern. The mod's additional settings are removed.
