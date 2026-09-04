---
navigation:
  parent: index.md
  title: Pattern P2P Tunnel (Output)
  icon: pattern_p2p_tunnel_output
  position: 21
item_ids:
- ae2_batchcraft:pattern_p2p_tunnel_output
---

# Pattern P2P Tunnel (Output)

<RecipeFor id="ae2_batchcraft:pattern_p2p_tunnel_output" />

Handles processing jobs for the machine in front of it, inserts ingredients through their encoded faces, and returns products to the Pattern Provider.

| Property | Value |
| --- | --- |
| AE channels | `0` |
| Frequency | Required |
| Active task | Required for task return and extraction |
| Supported transfer | AE-compatible items and fluids |
| Stored task limit | Up to `64` compatible jobs |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/output-machine.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Material Insertion

With automatic direction, material enters through the machine face touching the output. An explicitly encoded direction is an absolute world direction; the output does not fall back to its connected face if that side rejects the resource.

If the target accepts only part of a resource, the remainder stays pending and is retried. The endpoint is not available for an incompatible new task while pending material remains.

Full Dispatch requires this endpoint to accept the complete processing push. In Batch Distribution, the Input assigns an integer number of configured shares based on current capacity; this endpoint receives that assignment as one aggregated delivery.

## Product Return

Machines and pipes can push products into the output's insertion-only return capability. Active extraction can also pull from the adjacent machine when enabled on the input. Both paths apply the task's return configuration.

## Configuration and Energy

The output GUI directly shows Output Pages. General contains the product return mode, extraction interval, and extraction amount, with Sync on the right of its title. Output General contains the product extraction switch for this output. Task reset is available from the right toolbar.

While Sync is enabled, input broadcasts overwrite the output's local return mode, extraction switch, interval, and amount, and the corresponding controls cannot be edited. Disabling Sync preserves the last synchronized values and ignores later broadcasts. Re-enabling Sync immediately applies the current input values. New extraction requires an active, runnable task even in Unblocked mode; resources already pulled into the recovery queue may continue draining after the task ends.

A configured, active output also acts as a continuous FE destination for a Pattern P2P Energy Tunnel. It does not need an active crafting task to power the adjacent machine.

See [Batch Distribution](batch-distribution.md), [Material Output Directions](../troubleshooting/material-directions.md), and [Output Extraction](../product-return/endpoint-extraction.md).
