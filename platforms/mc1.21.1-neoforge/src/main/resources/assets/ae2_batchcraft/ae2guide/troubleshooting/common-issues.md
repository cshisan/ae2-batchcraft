---
navigation:
  parent: troubleshooting/index.md
  title: Common Issues
  position: 40
---

# Common Issues

## Endpoint Shows Unlinked

Check that its P2P frequency is not `0000`, the Input and endpoint are on the same AE subnet, the required chunks are loaded, and the AE node is active. Unit Ports additionally need a valid Unit Manager identity. Manager identity and P2P frequency are separate bindings.

## Materials Do Not Leave an Endpoint

Check the configured output face and output form, then inspect the machine inventory for capacity or side restrictions. Material delivery is retried; one blocked material can keep the task active even after products return.

## Products Do Not Return

Check the return mode, machine output face, active pattern, and ME storage capacity. A full ME network leaves products in the provider return inventory. AE2 retries them, and a successful retry wakes BatchCraft's source to resume immediate returning.

## Extraction Does Not Run

For normal Outputs, enable extraction on the Input. For Unit Extraction Ports, the switch is irrelevant but the Manager must have an operational task. In both cases verify interval, amount, machine-side capability, and available return capacity.

If a very large amount does not move in one tick, check ME capacity and whether several endpoints share the same AE grid. The amount is a per-pass budget; endpoint and grid round limits still apply.

## Energy Is Not Delivered

The Energy Tunnel needs an adjacent FE source or active input mode. A normal Output needs a nonzero frequency and active AE node, but not a crafting task. A Unit Energy Port needs a valid Manager binding and an FE-capable device in front, but also does not need an active task.

## A Large Job Is Not Split

Check that the Input uses **Batch Distribution** and that the encoded pattern has a batch count greater than `1`. Full Dispatch intentionally assigns the complete push to one endpoint.

If no materials move in Batch Distribution, verify that the Input directly faces the AE2 Pattern Provider, at least one endpoint can accept one smallest share, and the batch count preserves the actual minimum recipe ratio. A newly placed Input refuses materials without a matching active processing-pattern context.

See [Batch Distribution](../pattern-p2p/batch-distribution.md).

## Guide Shortcut

Hover an item from this mod and press AE2's guide key, `G`. The guide opens the item's leaf page and selects that exact node in the navigation tree.
