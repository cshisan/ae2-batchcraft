---
navigation:
  parent: product-return/index.md
  title: Product Return Configuration
  position: 10
---

# Product Return Configuration

The return mode controls which resource types an Output or Unit may accept while a task is active. It does not change ME storage capacity and does not enable active extraction.

| Mode | Accepted resources | Task sequencing | Recommended use |
| --- | --- | --- | --- |
| **Strict** | Product and byproduct types declared by the active pattern | Waits for the current batch before accepting a different pattern | Shared or contaminated return paths |
| **Unblocked** | Any returned resource | Does not block a new pattern on previous-job products | Isolated machine outputs |

Strict mode compares resource types. It does not prove that the machine completed a recipe, and it does not cap returned quantities to the pattern quantities. If another process can produce the same expected resource early, isolate that process or its return path.

The Input's general page is the central configuration point. Outputs and Unit Managers always keep local values. With synchronization enabled, input broadcasts overwrite the current local values; with synchronization disabled, broadcasts are ignored and local values remain in use. Re-enabling synchronization immediately applies the current input values. Changes made during an active task are propagated dynamically while synchronization is enabled.

Returning a product before every ingredient has been dispatched is allowed. A task cannot finish while its pending-material queue is nonempty, so early return does not discard ingredients still waiting for output.
