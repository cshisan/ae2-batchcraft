---
navigation:
  parent: index.md
  title: Fake Crafting Converter
  icon: fake_crafting_converter
  position: 150
item_ids:
- ae2_batchcraft:fake_crafting_converter
---

# Fake Crafting Converter

<ItemImage id="ae2_batchcraft:fake_crafting_converter" scale="3" />

Uses paired, named items as crafting tickets and delivers fake output tickets after the real product returns, allowing tickets to represent processing jobs.<br />&#160;<br />

## Usage

**Network and frequency:** Join the input's AE network and load the same frequency with a memory card. Only one converter may use a frequency on that network.<br />&#160;<br />

**Pair tickets:** Mark the input ticket in an upper slot and its fake output ticket below. They must be identical except for their custom names. Supply the initial physical tickets yourself.<br />&#160;<br />

**Prepare the pattern:** Add the input ticket to a [PP2MP pattern](pattern.md) and mark it as Fake Crafting. Put the corresponding fake output ticket in the first product slot.<br />&#160;<br />

**Mark the real output:** When a nonempty ingredient uses Fake Crafting, a marker slot appears above Encode. Mark the real product and middle-click to set the amount required for completion.<br />&#160;<br />

**Complete the job:** Once the real product matches the [return mode](configuration.md) and reaches the marked amount, fake output tickets return to the provider. Other ordinary byproducts need not all return first.<br />&#160;<br />

**Reuse tickets:** Connect a high-priority storage bus from the crafting network to any converter face, allowing both ticket types in and out. Deposited tickets become input tickets for the next job.<br />&#160;<br />

## Notes

**Complete pairs:** An upper marker alone is insufficient. Each fake input needs a pair, with corresponding fake output types and amounts in the pattern. Jobs cannot run without an available converter.<br />&#160;<br />

**Marking and insertion:** The first left-click marks without consuming an item. Clicking again after marking inserts it. Right-click clears markers when editing is allowed; tickets held for a job cannot be taken early.<br />&#160;<br />

**Separate marker:** The real output marker consumes no items and adds no pattern byproduct. The second product slot remains an ordinary byproduct slot; its amount does not increase the marker requirement. Real and fake outputs must differ.<br />&#160;<br />

**Check settings:** If encoding fails, check the indicated markers, products and quantities. The marker amount must also divide into batches. Clearing or importing a recipe requires setting the marker again. A valid pattern still needs a working network and machine.<br />&#160;<br />

**Later byproducts:** The job may end as soon as the real product returns. Arrange separate collection for later byproducts and keep both ticket recycling and provider return routes available.
