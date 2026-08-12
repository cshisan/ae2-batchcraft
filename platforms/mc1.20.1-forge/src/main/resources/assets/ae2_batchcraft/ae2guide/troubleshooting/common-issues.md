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

## Energy Is Not Delivered

The Energy Tunnel needs an adjacent FE source or active input mode. A normal Output needs a nonzero frequency and active AE node, but not a crafting task. A Unit Energy Port needs a valid Manager binding and an FE-capable device in front, but also does not need an active task.

## Jobs Are Not Split

This is expected. One complete processing job, such as `10000 A -> 10000 B`, is assigned to one eligible endpoint. Distribution occurs between jobs, not within one job.

## Guide Shortcut

Hover an item from this mod and press AE2's guide key, `G`. The guide opens the item's leaf page and selects that exact node in the navigation tree.
