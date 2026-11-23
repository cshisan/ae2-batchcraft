---
navigation:
  parent: index.md
  title: Pattern P2MP Input
  icon: pp2mp_input
  position: 20
item_ids:
- ae2_batchcraft:pp2mp_input
---

# Pattern P2MP Input

<ItemImage id="ae2_batchcraft:pp2mp_input" scale="3" />

Receives processing jobs from an adjacent pattern provider and assigns them to outputs or unit managers on the same frequency.<br />&#160;<br />

## Usage

**Connect the provider:** Point the provider at the input's front and bind endpoint frequencies as described in [Getting Started](getting_started.md).<br />&#160;<br />

**Job allocation:** Round-robin, random and priority settings determine which available output or manager is tried first.<br />&#160;<br />

**Delivery method:** Full dispatch sends the complete delivery to one endpoint. [Batch distribution](batch.md) divides it into smaller batches according to the pattern.<br />&#160;<br />

**Shared settings:** Synchronized outputs and managers can follow the input's settings to reduce repeated configuration.<br />&#160;<br />

## Notes

**Requirements:** The input needs power and a channel. Endpoints must share its AE network and frequency.<br />&#160;<br />

**Accepting another job:** Jobs wait when the target cannot accept materials. [Blocking mode](configuration.md) determines whether earlier products must return first.<br />&#160;<br />

**Reset scope:** Resetting an input affects jobs on endpoints sharing its frequency. Read the [reset notes](configuration.md) before using it.
