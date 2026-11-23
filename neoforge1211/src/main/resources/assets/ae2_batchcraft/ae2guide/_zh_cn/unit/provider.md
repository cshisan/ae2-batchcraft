---
navigation:
  parent: index.md
  title: 供应单元端口
  icon: pp2mp_unit_port_provider
  position: 140
item_ids:
- ae2_batchcraft:pp2mp_unit_port_provider
---

# 供应单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_provider" scale="3" />

把资源接收端提供的资源持续送入相邻机器，无合成任务时也可补充库存。<br />&#160;<br />

## 使用

**连接目标：** [绑定单元管理器](manager.md)，正面朝向目标库存。资源接收端与管理器需在同一 AE 网络并使用相同频率。<br />&#160;<br />

**维持库存：** 标记资源后，中键设置希望目标保有的数量；`0` 表示不限量。<br />&#160;<br />

**独立过滤：** 默认可跟随管理器的供应过滤；关闭同步后自行设置。反相卡可改为黑名单。<br />&#160;<br />

## 特殊使用说明

**数量含义：** 目标数量不是每次发送数量，黑名单不使用目标数量。详细规则见[供应过滤](../provider_filter.md)。<br />&#160;<br />

**持续供应：** 不需要等待或发起合成任务，目标机器仍需允许插入对应资源。
