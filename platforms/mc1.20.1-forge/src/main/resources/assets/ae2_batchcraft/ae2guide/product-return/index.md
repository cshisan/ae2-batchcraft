---
navigation:
  parent: index.md
  title: Product Return and Extraction
  position: 40
---

# Product Return and Extraction

Products can enter the AE network through a normal Output, a Unit return-capable port, or a Pattern Provider fitted with a Product Extraction Card. These paths share storage capacity but have separate configuration and task rules.

| Return path | Starts from | Needs an active P2P task | Configuration source |
| --- | --- | --- | --- |
| Normal Output return | Machine pushes into the Output | Yes | Output or synced Input |
| Normal Output extraction | Output pulls from the machine | Yes | Input |
| Unit Return/Collect/Break/Extraction | Unit port | Yes | Unit Manager or synced Input |
| Product Extraction Card | Pattern Provider pulls from a machine | No P2P task | Card GUI |

Returned resources first use the owning Pattern Provider's return inventory. When possible, BatchCraft asks the provider to flush existing return slots, inserts the new resource, and immediately tries to inject it into ME during the same call. This preserves AE2's normal return inventory and crafting-completion callbacks while avoiding the nine-slot inventory becoming a throughput bottleneck.

If ME storage is full, the resource remains in the return inventory. AE2's normal tick retries it. Once a later retry succeeds, BatchCraft wakes the source so high-throughput immediate returns can resume.

<SubPages />
