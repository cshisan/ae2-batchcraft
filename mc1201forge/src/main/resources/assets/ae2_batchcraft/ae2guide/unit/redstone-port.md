---
navigation:
  parent: unit/index.md
  title: Redstone Port
  icon: pattern_p2p_unit_port_redstone
  position: 90
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_redstone
---

# Unit Port (Redstone)

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_redstone" />

Produces strong and weak redstone power while its Unit task is operational.

| Mode | Output behavior |
| --- | --- |
| Single trigger | One pulse when the task starts |
| Periodic pulse | Repeats a pulse for the configured width and period |
| Continuous | Holds the configured strength for the task duration |

Signal strength is clamped from `0` to `15`. Pulse width and period are measured in ticks. The timing origin is when the port first observes the operational task; ending or losing the Manager clears its power output.

With synchronization enabled, redstone mode, strength, width, and period come from the input. An unsynchronized Manager uses its own redstone page.
