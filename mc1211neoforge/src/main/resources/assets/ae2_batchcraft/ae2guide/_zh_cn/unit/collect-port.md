---
navigation:
  parent: unit/index.md
  title: 收集端口
  icon: pattern_p2p_unit_port_collect
  position: 70
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_collect
---

# 单元端口（收集）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_collect" />

收集端口会收集正面方块空间中的掉落物，或收集正面方块中的源流体。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 可运行单元任务 | 必须存在 |
| 掉落物收集 | 正面方块空间中的物品实体 |
| 流体收集 | 正面方块中的源流体 |

收集资源会经过管理器当前返回规则。只有返回路径能够接收时，才会减少物品实体或移除源流体。

处理流程会把结果掉落到世界或产生源流体时使用收集端口；只有自动化主动推入时使用返回端口。
