---
navigation:
  parent: index.md
  title: Getting Started
  icon: pp2mp_input
  position: 10
---

# Getting Started

An input sends a pattern provider's processing jobs to outputs or unit managers, allowing multiple machines to share the workload.<br />&#160;<br />

## Choose a Route

**Ordinary machines:** Pattern provider → [input](input.md) → [output](output.md) → machine. Use this when materials and products can pass through the same connection.<br />&#160;<br />

**Separate connections or world processing:** Pattern provider → [input](input.md) → [unit manager](unit/manager.md) → unit ports. Use separate machine connections or combine dropping, placing, collecting, breaking and redstone functions.<br />&#160;<br />

## Usage

**Connect the network:** Point the provider at the input's front. Connect the input, outputs and managers to the same powered AE network. The input requires a channel.<br />&#160;<br />

**Bind frequencies:** Shift-right-click the input with a memory card to save its frequency, then right-click an output or manager to load it. Unit ports also need their manager's frequency and unit ID; see [binding instructions](unit/manager.md).<br />&#160;<br />

**Prepare patterns:** Put processing patterns in the provider. Use a [PP2MP pattern](pattern.md) for material output methods, empty-slot settings or batch configuration.<br />&#160;<br />

**Arrange product returns:** Let the machine push products back, enable an output's product extraction, or use an Extraction unit port. Products return to the original provider connected to the input.<br />&#160;<br />

## Notes

**Machine connections:** The connected faces must allow material insertion and the chosen return method. Configure the machine's own input and output settings as well.<br />&#160;<br />

**Waiting for jobs:** Disable [blocking](configuration.md) to feed another job while waiting for products. All materials from the previous delivery must still be sent first.
