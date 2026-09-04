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

## Product Return

Machines and pipes can push products into the output's insertion-only return capability. Active extraction can also pull from the adjacent machine when enabled for this output. Both paths apply the task's return configuration.

## Configuration and Energy

The output always has local return, extraction, interval, and amount values. With Sync enabled, input broadcasts overwrite those values; with Sync disabled, the output ignores broadcasts and uses its local values. The output-side extraction Enabled/Disabled buttons therefore control this output when Sync is off, while their state follows the input when Sync is on. New extraction is attempted only while this output has an active, runnable task; Unblocked mode does not bypass that task requirement. Resources already pulled but waiting for return remain in the recovery queue and may drain after the task ends.

A configured, active output also acts as a continuous FE destination for a Pattern P2P Energy Tunnel. It does not need an active crafting task to power the adjacent machine.

See [Material Output Directions](../troubleshooting/material-directions.md) and [Output Extraction](../product-return/endpoint-extraction.md).
