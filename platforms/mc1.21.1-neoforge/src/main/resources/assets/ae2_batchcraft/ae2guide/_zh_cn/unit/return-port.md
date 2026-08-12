---
navigation:
  parent: unit/index.md
  title: 返回端口
  icon: pattern_p2p_unit_port_return
  position: 50
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_return
---

# 单元端口（返回）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_return" />

向端口正面的设备或管道提供只入不出的物品和流体返回能力。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 可运行单元任务 | 必须存在 |
| 资源 | 物品和流体 |
| 传输方向 | 相邻设备主动推入端口 |

返回端口不会主动拉取。资源先通过管理器当前返回模式，再进入样板供应器返回路径；严格模式拒绝活动样板未声明的类型。

机器无法主动推送时使用[提取端口](extraction-port.md)。
