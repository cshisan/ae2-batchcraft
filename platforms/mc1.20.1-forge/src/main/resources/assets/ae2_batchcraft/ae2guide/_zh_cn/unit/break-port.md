---
navigation:
  parent: unit/index.md
  title: 破坏端口
  icon: pattern_p2p_unit_port_break
  position: 80
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_break
---

# 单元端口（破坏）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_break" />

单元任务可运行时，使用 AE2 世界拾取策略破坏端口正面的方块或收集源流体。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 可运行单元任务 | 必须存在 |
| 目标 | 可破坏方块与源流体 |
| 配置 | 破坏回收 |

开启**破坏回收**后，可接收掉落物直接经管理器返回；关闭后，物品掉落物生成在端口正面的世界中。源流体无法表示为世界物品实体，因此两种模式下都直接返回。

实际拾取前会检查返回规则和可用容量。领地或保护规则可能阻止世界交互。
