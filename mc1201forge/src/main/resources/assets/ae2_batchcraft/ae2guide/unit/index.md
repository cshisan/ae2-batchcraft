---
navigation:
  parent: index.md
  title: Pattern P2P Unit
  icon: pattern_p2p_unit_manager
  position: 30
---

# Pattern P2P Unit

A Unit combines one Manager with any number of directly listed functional ports. The Manager receives one complete processing job; its ports route materials, interact with the world, return products, produce redstone, or deliver FE.

<GameScene zoom="3.5" interactive={true}>
  <ImportStructure src="../assets/assemblies/unit-overview.snbt" />
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

| Port group | Task requirement |
| --- | --- |
| Transfer, Drop, Place | Operate while dispatching a Unit task |
| Return, Collect, Break, Extraction | Return products for an operational Unit task |
| Redstone | Outputs while the Unit task is operational |
| Energy | Operates continuously after valid Manager binding |

The Energy Port is the exception to the Unit's task gating. It does not turn off when the Manager has no crafting task.

<SubPages />
