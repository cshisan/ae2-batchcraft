---
navigation:
  parent: unit/index.md
  title: 传输端口
  icon: pattern_p2p_unit_port_transfer
  position: 20
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_transfer
---

# 单元端口（传输）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_transfer" />

将编码为**普通**输出形式的材料插入端口正面的库存或储罐。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 活动任务 | 实际插入时必须存在 |
| 资源 | AE 兼容的物品和流体 |
| 方向 | 端口正面 |

管理器在接收任务前探测端口。下发时如果只插入一部分，余量会保持待下发并继续重试。模拟阶段可以成功，但管理器任务未运行时不会修改世界。

机器需要不同库存或储罐时可使用多个传输端口；管理器为每种材料选择第一个兼容的绑定端口。另见[材料输出方向](../troubleshooting/material-directions.md)。
