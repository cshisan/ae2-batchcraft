---
navigation:
  parent: index.md
  title: 传输单元端口
  icon: pp2mp_unit_port_transfer
  position: 60
item_ids:
- ae2_batchcraft:pp2mp_unit_port_transfer
---

# 传输单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_transfer" scale="3" />

把样板中使用普通输出方式的材料送入相邻机器库存或储罐。<br />&#160;<br />

## 使用

**连接机器：** [绑定单元管理器](manager.md)，将正面对准机器允许输入的一面。<br />&#160;<br />

**筛选与优先级：** 标记允许投送的材料；多个端口可接收同种材料时，可设置优先级。<br />&#160;<br />

**投料模式：** 支持普通、槽位独占、单个物品和同类型，规则见[单元管理器](manager.md)。管理器集中设置，端口关闭同步后可独立设置。<br />&#160;<br />

## 特殊使用说明

**具体资源：** 同类型模式下，圆石与橡木不能共用一个端口投送；同一种资源可以跨材料槽使用该端口。<br />&#160;<br />

**目标容量：** 机器自身的插入限制仍然有效。端口模式不会绕过机器容量或资源限制。<br />&#160;<br />

**产物返回：** 本端口只负责投料，需另配返回、提取等产物获取端口。
