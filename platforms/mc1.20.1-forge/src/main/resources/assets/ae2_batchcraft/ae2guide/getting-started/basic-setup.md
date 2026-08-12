---
navigation:
  parent: getting-started/index.md
  title: Basic Setup
  icon: pattern_p2p_tunnel_input
  position: 10
---

# Basic Setup

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/basic-pattern-p2p.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Required Components

| Component | Purpose |
| --- | --- |
| Pattern Provider | Holds processing patterns and starts external processing |
| Pattern P2P Input | Receives jobs from the Pattern Provider |
| Pattern P2P Output | Sends one selected job to its adjacent machine |
| AE subnet cable | Connects the input and outputs to the same AE grid |
| Memory Card | Creates and copies the P2P frequency |

## Build Steps

1. Put a Pattern Provider on the main crafting network and aim its output face at the Pattern P2P Input.
2. Connect the input to an AE subnet. The input consumes `1` channel on this subnet.
3. Put one output on the same subnet in front of each machine. Aim the output's front face at the machine.
4. Bind all endpoints to one nonzero frequency.
5. Insert a processing pattern into the Pattern Provider and request a job.

The input checks outputs and Unit Managers on its frequency in round-robin order. Busy, offline, unloaded, or currently incompatible endpoints are skipped. Once accepted, the complete job belongs to that endpoint.

## First-Run Checklist

- The input, outputs, and Unit Managers are on the same loaded AE grid.
- The input shows a nonzero frequency and is active.
- Every output has the same frequency and faces a machine inventory.
- The Pattern Provider points directly toward the input.
- The processing pattern declares the actual product that will return.
- ME storage has room for the returned product.

See [Frequency Binding](frequency.md) if an endpoint does not join the group, and [Product Return Flow](return-flow.md) if ingredients move but products do not return.
