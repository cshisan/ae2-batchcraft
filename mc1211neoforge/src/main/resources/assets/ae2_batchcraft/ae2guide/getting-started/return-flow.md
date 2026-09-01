---
navigation:
  parent: getting-started/index.md
  title: Product Return Flow
  position: 30
---

# Product Return Flow

Products return through the endpoint that owns the job. A normal output uses its return inventory; a Unit uses a Return, Collect, Break, or Extraction Port and then the Manager's return path.

## Return Sequence

1. The endpoint applies the active return rule.
2. If the Pattern Provider already has stored return stacks, the endpoint requests an immediate flush first.
3. The accepted resource enters the Pattern Provider return inventory.
4. The same call attempts to inject it into ME storage through AE2's normal crafting return path.
5. AE2's original ticker remains responsible for anything that could not enter the network.

The existing return inventory is checked before an immediate flush is requested, so empty providers do not receive pointless flush calls.

## When ME Storage Is Full

The product remains stored; it is not deleted. AE2 keeps retrying through its original tick behavior. When a later retry successfully moves a resource into the network, the producer is woken so immediate high-throughput returns can continue.

## Task Completion

Returning the declared primary product can complete the product side of a task early. Pending ingredients are tracked separately, so the endpoint does not discard material that has not yet been sent.

See [Product Return Configuration](../product-return/return-configuration.md) for filtering rules and [Output Extraction](../product-return/endpoint-extraction.md) for active pulling.
