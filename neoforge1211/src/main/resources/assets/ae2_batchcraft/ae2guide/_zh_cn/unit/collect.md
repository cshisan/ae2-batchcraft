---
navigation:
  parent: index.md
  title: 收集单元端口
  icon: pp2mp_unit_port_collect
  position: 110
item_ids:
- ae2_batchcraft:pp2mp_unit_port_collect
---

# 收集单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_collect" scale="3" />

收集正前方一格内的掉落物和可拾取的世界流体，经管理器返回样板供应器。<br />&#160;<br />

## 使用

**选择位置：** [绑定单元管理器](manager.md)，将正面对准产物出现的位置。<br />&#160;<br />

**筛选与间隔：** 可标记允许收集的资源，间隔使用管理器的提取参数，最小为 `1 tick`。<br />&#160;<br />

## 特殊使用说明

**需要任务：** 单元需有可运行任务，收集的资源受管理器[返回模式](../configuration.md)限制。<br />&#160;<br />

**收集范围：** 只处理前方一格，不从库存提取，也不破坏实体方块。<br />&#160;<br />

**收集数量：** 掉落物可能一次收取整堆，管理器的数量设置不能直接理解为每轮收取的物品个数上限。<br />&#160;<br />

**混有其他物品：** 无法接收的掉落物会跳过，其他符合条件的产物仍会尝试收集；实际收取量受返回目标容量限制。
