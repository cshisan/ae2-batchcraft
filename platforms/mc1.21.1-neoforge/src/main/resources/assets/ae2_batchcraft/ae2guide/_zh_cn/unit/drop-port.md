---
navigation:
  parent: unit/index.md
  title: 投掷端口
  icon: pattern_p2p_unit_port_drop
  position: 30
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_drop
---

# 单元端口（投掷）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_drop" />

将编码为**掉落**输出形式的物品材料，以物品实体释放到端口正面的方块空间。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 活动任务 | 必须存在 |
| 资源 | 仅物品 |
| 目标 | 正面方块空间 |

编码资源不能使用掉落形式，或没有兼容投掷端口时，管理器会拒绝任务。请保持正面空间已加载且无阻挡，并提供适当机器或收集系统。

材料被投掷即视为完成下发；单元不会等待机器拾取物品实体。
