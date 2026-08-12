---
navigation:
  parent: unit/index.md
  title: 提取端口
  icon: pattern_p2p_unit_port_extract
  position: 60
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_extract
---

# 单元端口（提取）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_extract" />

单元任务可运行时，主动从端口正面的机器拉取产物。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 可运行单元任务 | 必须存在 |
| 资源 | AE 兼容的物品和流体 |
| 输入端提取开关 | 此端口忽略 |
| 生效设置 | 提取间隔和数量 |

输入端提取开关只控制普通输出端。开关下方的间隔和数量仍作用于同步的提取端口；未同步管理器使用本地值。

提取可在材料全部下发前开始。单元单独保存待下发材料，发送完毕前不能结束。空提取采用渐进退避，最多额外 `20` tick；能力变化、返回容量和恢复进度可提前唤醒，但成功提取后不会缩短配置的最小间隔。

已拉出但无法立即返回的资源会进入恢复队列重试，不会静默删除。另见[端点主动提取](../product-return/endpoint-extraction.md)。
