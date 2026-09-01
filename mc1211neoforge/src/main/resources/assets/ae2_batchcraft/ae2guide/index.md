---
navigation:
  title: AE2 BatchCraft
  icon: pattern_p2p_tunnel_input
  position: 1000
---

# AE2 BatchCraft

<Row gap="16">
  <ItemImage id="ae2_batchcraft:pattern_p2p_tunnel_input" scale="3" />
  <ItemImage id="ae2_batchcraft:pattern_p2p_tunnel_output" scale="3" />
  <ItemImage id="ae2_batchcraft:pattern_p2p_unit_manager" scale="3" />
  <ItemImage id="ae2_batchcraft:component_placer" scale="3" />
</Row>

Distribute processing jobs from one AE2 Pattern Provider among multiple endpoints. A normal Output handles one adjacent inventory directly; a Unit Manager coordinates specialized ports for jobs that need several material or world interactions.

| Property | Behavior |
| --- | --- |
| Input channel cost | `1` AE channel |
| Output and Unit cost | No additional AE channels |
| Distribution modes | Full Dispatch and Batch Distribution |
| Returned resources | Items and fluids supported by AE storage APIs |

Start with **Getting Started** for a working network. Open a component's own page by hovering it and pressing AE2's guide key, `G`. See [Batch Distribution](pattern-p2p/batch-distribution.md) before splitting a large pattern.

<SubPages />
