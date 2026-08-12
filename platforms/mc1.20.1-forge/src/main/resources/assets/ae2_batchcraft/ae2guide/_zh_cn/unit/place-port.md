---
navigation:
  parent: unit/index.md
  title: 放置端口
  icon: pattern_p2p_unit_port_place
  position: 40
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_place
---

# 单元端口（放置）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_place" />

把编码为**放置**输出形式且受支持的方块物品或流体放入端口正面的世界空间。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 活动任务 | 必须存在 |
| 资源 | 可放置方块和流体 |
| 目标 | 正面方块空间 |

资源不支持世界放置，或没有兼容放置端口时，管理器会拒绝任务。目标暂时被占用或不合法时，材料保持待下发并继续重试。

世界放置使用所属玩家的自动化上下文，因此保护模组或领地规则可能阻止操作。
