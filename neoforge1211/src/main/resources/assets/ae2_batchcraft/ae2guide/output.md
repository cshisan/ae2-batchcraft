---
navigation:
  parent: index.md
  title: Pattern P2MP Output
  icon: pp2mp_output
  position: 30
item_ids:
- ae2_batchcraft:pp2mp_output
---

# Pattern P2MP Output

<ItemImage id="ae2_batchcraft:pp2mp_output" scale="3" />

Connects one adjacent machine, delivers materials assigned by an input, and sends products back to the pattern provider.<br />&#160;<br />

## Usage

**Connect the machine:** Point the output at the machine, connect it to the input's AE network, and load the input's frequency with a memory card.<br />&#160;<br />

**Return products:** Receive products pushed by the machine, or enable product extraction to pull them. See [Common Settings](configuration.md) for the extraction options.<br />&#160;<br />

**Continuous supply:** Use a [resource intake](resource_intake.md) to replenish the machine. [Supply filtering](provider_filter.md) controls allowed resources and target stock.<br />&#160;<br />

**Synchronize settings:** Job settings can follow the input, and supply filters can follow the intake. Disable the relevant synchronization to configure them independently.<br />&#160;<br />

## Notes

**Machine faces:** The connected face must allow the required operation. A pattern's output direction selects a face of that same adjacent machine.<br />&#160;<br />

**Jobs and supply:** Continuous supply needs no crafting job. Crafting product returns require an operational job.<br />&#160;<br />

**Further deliveries:** [Blocking mode](configuration.md) controls whether another job can be accepted while waiting for products.
