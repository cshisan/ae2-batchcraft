---
navigation:
  parent: index.md
  title: Pattern P2MP Resource Intake
  icon: pp2mp_resource_intake
  position: 40
item_ids:
- ae2_batchcraft:pp2mp_resource_intake
---

# Pattern P2MP Resource Intake

<ItemImage id="ae2_batchcraft:pp2mp_resource_intake" scale="3" />

Acquires resources from an adjacent source and continuously supplies machines through outputs or Supply ports, without a crafting job.<br />&#160;<br />

## Usage

**Acquire resources:** Passive mode accepts resources pushed by the source. Active mode pulls them at a configurable interval and amount per cycle; the minimum interval is `1 tick`.<br />&#160;<br />

**Choose destinations:** Connect the intake and destination outputs or managers to the same AE network and frequency. Resources go to the machines, not into ME storage.<br />&#160;<br />

**Distribution and filters:** Choose round-robin, priority or even distribution. [Supply filtering](provider_filter.md) restricts resources and target stock.<br />&#160;<br />

## Notes

**No frequency:** An unconfigured intake cannot supply ordinary resources to outputs or Supply ports.<br />&#160;<br />

**Network power:** Supported Applied Flux FE resources can charge the local AE network. This requires neither a frequency nor an already powered network, but does not support direct connections from ordinary FE energy cables.<br />&#160;<br />

**Unavailable destinations:** Supply waits for a usable destination. Actual amounts depend on available stock, machine connections and destination capacity.
