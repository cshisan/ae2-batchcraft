---
navigation:
  parent: index.md
  title: Pattern P2P Tunnel (Energy)
  icon: pattern_p2p_tunnel_energy
  position: 22
item_ids:
- ae2_batchcraft:pattern_p2p_tunnel_energy
---

# Pattern P2P Tunnel (Energy)

<RecipeFor id="ae2_batchcraft:pattern_p2p_tunnel_energy" />

Accepts FE from the block against its front face, powers its AE subnet first, and distributes the remaining FE to eligible normal outputs and Unit Energy Ports.

| Property | Value |
| --- | --- |
| AE channels | `0` |
| Frequency | Not used by the energy tunnel |
| Input resource | FE |
| Output scope | Current AE grid |
| Crafting task | Not required for energy delivery |

## Input Modes

| Mode | Behavior |
| --- | --- |
| Passive receive | Accepts FE only when the adjacent source pushes it |
| Active pull | Accepts pushed FE and also requests FE from the source |

## Eligible Destinations

A normal output must have a nonzero frequency and an active AE node. A Unit Energy Port must be bound to a valid Manager on the same grid. **Neither destination needs an active crafting task to receive FE.**

## Distribution Modes

| Mode | Behavior |
| --- | --- |
| Even | Shares available FE across current receiver demand as evenly as possible |
| Round robin | Prioritizes receivers in a rotating order, useful when supply cannot satisfy all demand |

Changing the distribution mode synchronizes it across Pattern P2P outputs and Unit Managers on the current AE grid. Receivers are grouped by output frequency or Unit identity, then their configured mode is applied within the group.

The energy tunnel never forwards FE into another energy tunnel placed as its target.

See [Unit Energy Port](../unit/energy-port.md) for its continuous-power behavior.
