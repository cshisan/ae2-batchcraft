---
navigation:
  parent: index.md
  title: Redstone Unit Port
  icon: pp2mp_unit_port_redstone
  position: 130
item_ids:
- ae2_batchcraft:pp2mp_unit_port_redstone
---

# Redstone Unit Port

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_redstone" scale="3" />

Outputs redstone signals during unit jobs to start or control adjacent devices.<br />&#160;<br />

## Usage

**Configure:** [Bind a unit manager](manager.md), then set mode, strength, pulse width and period on its Redstone page. Timing uses `ticks`.<br />&#160;<br />

**Single trigger:** Outputs one pulse for each job.<br />&#160;<br />

**Periodic pulse:** Repeats at the configured interval during the job.<br />&#160;<br />

**Continuous output:** Keeps the signal on during the job.<br />&#160;<br />

## Notes

**Trigger condition:** Signals follow unit jobs. Continuous resource supply alone does not trigger them.<br />&#160;<br />

**Concurrent jobs:** With [blocking disabled](../configuration.md), pulses may overlap. Keep blocking enabled when the machine needs a separate trigger for every job.
