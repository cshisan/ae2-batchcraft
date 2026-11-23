---
navigation:
  parent: pattern.md
  title: Batch Distribution
  position: 10
---

# Batch Distribution

Divides a processing recipe into a fixed number of smaller batches, reducing each delivery for machines that cannot hold the whole recipe at once.<br />&#160;<br />

## Usage

**Set the count:** `Ctrl-middle-click` the first product in the PP2MP editor, set the batch count and encode the pattern. Select batch distribution on the input.<br />&#160;<br />

**Example:** `4A + 4B → 4C` split into `2` batches sends `2A + 2B` and requires `2C` per batch.<br />&#160;<br />

**Further batches:** [Blocking mode](configuration.md) determines whether earlier products must return first. Accepted materials wait when no endpoint is available.<br />&#160;<br />

## Notes

**Exact division:** The batch count must divide every ingredient, product and fake-crafting real output marker amount.<br />&#160;<br />

**Machine recipes:** Each batch must satisfy the machine's actual recipe. If it needs `2A + 2B`, the example cannot be split into `4` batches. Batch size does not automatically shrink to fit machine capacity.
