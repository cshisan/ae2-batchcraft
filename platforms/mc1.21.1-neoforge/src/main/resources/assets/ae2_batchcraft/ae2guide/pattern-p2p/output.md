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

Machines and pipes can push products into the output's insertion-only return capability. Active extraction can also pull from the adjacent machine when enabled on the input. Both paths apply the task's return configuration.

## Configuration and Energy

The output follows the input's return mode by default. Disable synchronization to choose a local return mode. The input's extraction switch controls normal outputs; its interval and amount set the extraction schedule.

A configured, active output also acts as a continuous FE destination for a Pattern P2P Energy Tunnel. It does not need an active crafting task to power the adjacent machine.

See [Material Output Directions](../troubleshooting/material-directions.md) and [Output Extraction](../product-return/endpoint-extraction.md).
