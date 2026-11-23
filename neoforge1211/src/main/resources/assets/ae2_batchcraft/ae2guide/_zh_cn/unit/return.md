---
navigation:
  parent: index.md
  title: 返回单元端口
  icon: pp2mp_unit_port_return
  position: 90
item_ids:
- ae2_batchcraft:pp2mp_unit_port_return
---

# 返回单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_return" scale="3" />

接收机器或管道主动推入的产物，经管理器返回原样板供应器。<br />&#160;<br />

## 使用

**连接来源：** [绑定单元管理器](manager.md)，将机器或管道的产物输出连接到端口正面。<br />&#160;<br />

**筛选产物：** 可设置端口过滤，并遵循管理器的[返回模式](../configuration.md)。<br />&#160;<br />

## 特殊使用说明

**需要任务：** 单元有可运行任务时才能返回对应产物。<br />&#160;<br />

**不会主动提取：** 来源需要主动推入。机器不能自动输出时，可使用[提取端口](extract.md)。<br />&#160;<br />

**返回堵塞：** 本端口不能代替库存暂存产物，请保证返回路径能接收资源。
