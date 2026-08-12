---
navigation:
  parent: unit/index.md
  title: 红石端口
  icon: pattern_p2p_unit_port_redstone
  position: 90
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_redstone
---

# 单元端口（红石）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_redstone" />

单元任务可运行期间输出强、弱红石信号。

| 模式 | 输出行为 |
| --- | --- |
| 单次触发 | 任务开始时输出一次脉冲 |
| 周期脉冲 | 按配置宽度和周期重复输出脉冲 |
| 持续输出 | 任务期间保持配置的信号强度 |

信号强度限制在 `0` 到 `15`，脉冲宽度和周期单位为 tick。计时起点是端口首次观察到可运行任务时；任务结束或失去管理器后立即清除输出。

开启同步时，红石模式、强度、宽度和周期来自输入端；未同步管理器使用自己的红石分页。
